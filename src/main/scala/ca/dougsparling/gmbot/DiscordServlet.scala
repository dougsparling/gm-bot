package ca.dougsparling.gmbot

import ca.dougsparling.gmbot.parser.{RollSpec, RollSpecParser, NoDrop, DropLowest, DropHighest}
import ca.dougsparling.gmbot.roller.{RollRenderer, RollSpecApproximator, RollSpecRunner, SecureDice, SecureGaussian}
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
            val content = handleRoll(spec, who, reroll = false)
            compact(render(rollResponse(content, spec)))

          case "ability" =>
            val options  = (json \ "data" \ "options").extract[List[JValue]]
            def opt(name: String) = options.find(o => (o \ "name").extractOpt[String].contains(name))
            val rollType = opt("roll").flatMap(o => (o \ "value").extractOpt[String]).getOrElse("straight")
            val modifier = opt("modifier").flatMap(o => (o \ "value").extractOpt[Int]).getOrElse(0)
            val rollSpec = rollType match
              case "advantage"    => RollSpec(1, 4, 6, modifier, None, DropLowest(1))
              case "disadvantage" => RollSpec(1, 4, 6, modifier, None, DropHighest(1))
              case _              => RollSpec(1, 3, 6, modifier, None, NoDrop)
            val who     = resolveDisplayName(json)
            val content = renderRollSpec(rollSpec, who, reroll = false)
            val specStr = rollType match
              case "advantage"    => s"4d6${modStr(modifier)} drop lowest"
              case "disadvantage" => s"4d6${modStr(modifier)} drop highest"
              case _              => s"3d6${modStr(modifier)}"
            compact(render(rollResponse(content, specStr)))

          case _ =>
            halt(400, "unknown command")

      case 3 =>
        // MESSAGE_COMPONENT (button)
        val customId = (json \ "data" \ "custom_id").extractOpt[String].getOrElse("")
        if customId.startsWith("reroll:") then
          val spec    = customId.stripPrefix("reroll:")
          val who     = resolveDisplayName(json)
          val content = handleRoll(spec, who, reroll = true)
          compact(render(rollResponse(content, spec)))
        else
          halt(400, "unknown component")

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

  private def modStr(modifier: Int): String =
    if modifier > 0 then s"+$modifier"
    else if modifier < 0 then modifier.toString
    else ""

  private def rerollButton(spec: String) =
    JObject("type" -> JInt(1), "components" -> JArray(List(
      JObject(
        "type"      -> JInt(2),
        "label"     -> JString("Reroll"),
        "style"     -> JInt(1),
        "custom_id" -> JString(s"reroll:$spec".take(100))
      )
    )))

  private def rollResponse(content: String, spec: String) =
    JObject("type" -> JInt(4), "data" -> JObject(
      "content"    -> JString(content),
      "components" -> JArray(List(rerollButton(spec)))
    ))

  private def handleRoll(spec: String, who: String, reroll: Boolean): String =
    RollSpecParser.parseAll(RollSpecParser.roll, spec) match
      case RollSpecParser.NoSuccess(hint, _) => RollRenderer.helpText(hint)
      case RollSpecParser.Success(rollSpec, _) => renderRollSpec(rollSpec, who, reroll)

  private def renderRollSpec(rollSpec: RollSpec, who: String, reroll: Boolean): String =
    val verb = if reroll then "Rerolled" else "Rolled"
    RollRenderer.validateSpec(rollSpec).getOrElse {
      if rollSpec.shouldApproximate then
        RollRenderer.formatApproximate(approximator.approximate(rollSpec), rollSpec, who, verb)
      else
        roller.run(rollSpec) match
          case Right(err)   => err
          case Left(result) =>
            s"${RollRenderer.formatSummary(result, rollSpec, who, verb)}\n${RollRenderer.formatBatches(result, rollSpec)}"
    }
}
