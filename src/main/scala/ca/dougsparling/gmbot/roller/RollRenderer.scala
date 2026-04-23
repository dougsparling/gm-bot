package ca.dougsparling.gmbot.roller

import ca.dougsparling.gmbot.parser.RollSpec

object RollRenderer:

  private lazy val roller       = new RollSpecRunner with SecureDice
  private lazy val approximator = new RollSpecApproximator with SecureGaussian

  /** Returns an error message if the spec fails validation, None if it can proceed. */
  def validateSpec(spec: RollSpec): Option[String] =
    if spec.die >= 10000 then Some(s"Sorry, I can't find a ${spec.die} sided die.")
    else if spec.reroll.exists(_.size >= spec.die) then Some("All dice would be rerolled.")
    else None

  /** Base help text with optional parser hint appended. */
  def helpText(hint: String = ""): String =
    val base = "Usage: `[N times] [N]dS [+M] [reroll N|N to M] [drop highest|lowest [N]]`\n" +
               "e.g. `4d6 drop lowest`, `d20+15`, `7d10 reroll 1 to 2`"
    if hint.nonEmpty then s"$base\n$hint" else base

  /**
   * Executes a roll and returns a fully rendered string.
   * Handles validation, approximation, and Left/Right from the runner.
   * verb defaults to "Rolled"; pass "Rerolled" for reroll responses.
   */
  def render(spec: RollSpec, who: String, verb: String = "Rolled", label: Option[String] = None): String =
    validateSpec(spec).getOrElse {
      if spec.shouldApproximate then
        formatApproximate(approximator.approximate(spec), spec, who, verb, label)
      else
        roller.run(spec) match
          case Right(err)   => err
          case Left(result) => s"${formatSummary(result, spec, who, verb, label)}\n${formatBatches(result, spec)}"
    }

  /** "Rolled for Alice: 14, 12" or "Rolled Might for Alice: 14, 12" when label is given. */
  def formatSummary(result: Result, spec: RollSpec, who: String, verb: String = "Rolled", label: Option[String] = None): String =
    val totals  = result.batches.map(_.sum(spec.modifier)).mkString(", ")
    val subject = label.fold(s"$verb for")(l => s"$verb $l for")
    s"$subject $who: $totals"

  /** "Rolled for Alice: ≈14 _(statistical approximation)_" */
  def formatApproximate(result: Result, spec: RollSpec, who: String, verb: String = "Rolled", label: Option[String] = None): String =
    val totals  = result.batches.map(b => s"≈ ${b.sum(spec.modifier)}").mkString(", ")
    val subject = label.fold(s"$verb for")(l => s"$verb $l for")
    s"$subject $who: $totals _(statistical approximation)_"

  /** Per-batch detail lines, e.g. "2, 5 (dropped), 4 = 11\n3, 6 = 9" */
  def formatBatches(result: Result, spec: RollSpec): String =
    result.batches.map { batch =>
      val tally = batch.rolls.map {
        case Roll(n, Kept)     => s"$n"
        case Roll(n, Rerolled) => s"$n (rerolled)"
        case Roll(n, Dropped)  => s"$n (dropped)"
      }.mkString(", ")
      val mod = if spec.modifier == 0 then "" else s" + ${spec.modifier}"
      s"$tally$mod = ${batch.sum(spec.modifier)}"
    }.mkString("\n")
