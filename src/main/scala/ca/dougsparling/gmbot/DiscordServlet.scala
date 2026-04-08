package ca.dougsparling.gmbot

import ca.dougsparling.gmbot.parser.RollSpecParser
import ca.dougsparling.gmbot.roller.{RollSpecApproximator, RollSpecRunner, SecureDice, SecureGaussian, Kept, Rerolled, Dropped}
import org.slf4j.LoggerFactory
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatra.json._
import org.scalatra.Ok

import java.security.{KeyFactory, Signature}
import java.security.spec.X509EncodedKeySpec

class DiscordServlet extends GmBotStack with JacksonJsonSupport {

  protected lazy val logger = LoggerFactory.getLogger(getClass)
  protected implicit lazy val jsonFormats: Formats = DefaultFormats
  protected lazy val roller       = new RollSpecRunner with SecureDice
  protected lazy val approximator = new RollSpecApproximator with SecureGaussian

  private lazy val publicKeyHex = sys.env("DISCORD_PUBLIC_KEY").trim

  // Ed25519 DER header for X509EncodedKeySpec — avoids Bouncy Castle dependency
  private val DER_PREFIX = Array[Byte](
    0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
  )

  private def hexToBytes(hex: String): Array[Byte] =
    hex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray

  private def verifySignature(timestamp: String, body: String, signature: String): Boolean =
    try
      val keyBytes = DER_PREFIX ++ hexToBytes(publicKeyHex)
      val pubKey   = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(keyBytes))
      val sig      = Signature.getInstance("Ed25519")
      sig.initVerify(pubKey)
      sig.update((timestamp + body).getBytes("UTF-8"))
      sig.verify(hexToBytes(signature))
    catch case _: Exception => false

  before() {
    contentType = formats("json")
  }

  post("/interactions") {
    val rawBody   = request.body
    val timestamp = Option(request.getHeader("X-Signature-Timestamp")).getOrElse("")
    val signature = Option(request.getHeader("X-Signature-Ed25519")).getOrElse("")

    if !verifySignature(timestamp, rawBody, signature) then
      halt(401, "invalid signature")

    val json            = parse(rawBody)
    val interactionType = (json \ "type").extractOpt[Int].getOrElse(0)

    interactionType match
      case 1 =>
        // PING — Discord endpoint verification
        compact(render(JObject("type" -> JInt(1))))

      case 2 =>
        // APPLICATION_COMMAND
        val commandName = (json \ "data" \ "name").extractOpt[String].getOrElse("")
        commandName match
          case "roll" =>
            val spec    = ((json \ "data" \ "options")(0) \ "value").extractOpt[String].getOrElse("")
            val who     = resolveDisplayName(json)
            val content = handleRoll(spec, who)
            compact(render(JObject("type" -> JInt(4), "data" -> JObject("content" -> JString(content)))))
          case _ =>
            halt(400, "unknown command")

      case _ =>
        halt(400, "unsupported interaction type")
  }

  private def resolveDisplayName(json: JValue): String =
    // Guild context: member.nick > member.user.global_name > member.user.username
    // DM/user-install context: user.global_name > user.username
    val memberNick       = (json \ "member" \ "nick").extractOpt[String].filter(_.nonEmpty)
    val memberGlobalName = (json \ "member" \ "user" \ "global_name").extractOpt[String].filter(_.nonEmpty)
    val memberUsername   = (json \ "member" \ "user" \ "username").extractOpt[String].filter(_.nonEmpty)
    val userGlobalName   = (json \ "user" \ "global_name").extractOpt[String].filter(_.nonEmpty)
    val userUsername     = (json \ "user" \ "username").extractOpt[String].filter(_.nonEmpty)
    memberNick
      .orElse(memberGlobalName)
      .orElse(memberUsername)
      .orElse(userGlobalName)
      .orElse(userUsername)
      .getOrElse("unknown")

  private def handleRoll(spec: String, who: String): String =
    RollSpecParser.parseAll(RollSpecParser.roll, spec) match
      case RollSpecParser.NoSuccess(_, _) =>
        "Usage: `/roll [N times] [N]dS [+M] [reroll N|N to M] [drop highest|lowest [N]]`\ne.g. `4d6 drop lowest`, `d20+15`, `7d10 reroll 1 to 2`"
      case RollSpecParser.Success(rollSpec, _) if rollSpec.die >= 10000 =>
        s"Sorry, I can't find a ${rollSpec.die} sided die."
      case RollSpecParser.Success(rollSpec, _) if rollSpec.reroll.exists(_.size >= rollSpec.die) =>
        "All dice would be rerolled."
      case RollSpecParser.Success(rollSpec, _) if rollSpec.shouldApproximate =>
        val result = approximator.approximate(rollSpec)
        val totals = result.batches.map(b => s"≈ ${b.sum(rollSpec.modifier)}").mkString(", ")
        s"Rolled for $who: $totals _(statistical approximation)_"
      case RollSpecParser.Success(rollSpec, _) =>
        roller.run(rollSpec) match
          case Right(err) => err
          case Left(result) =>
            val totals  = result.batches.map(_.sum(rollSpec.modifier)).mkString(", ")
            val details = result.batches.map { batch =>
              val tally = batch.rolls.map {
                case r if r.fate == Kept     => s"${r.n}"
                case r if r.fate == Rerolled => s"${r.n} (rerolled)"
                case r if r.fate == Dropped  => s"${r.n} (dropped)"
              }.mkString(", ")
              val mod = if rollSpec.modifier == 0 then "" else s" + ${rollSpec.modifier}"
              s"$tally$mod = ${batch.sum(rollSpec.modifier)}"
            }.mkString("\n")
            s"Rolled for $who: $totals\n$details"
}
