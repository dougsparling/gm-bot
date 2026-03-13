package ca.dougsparling.gmbot.rulebook

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

sealed trait RulebookResult
case class Found(name: String, path: Path) extends RulebookResult
case class Ambiguous(matches: List[String]) extends RulebookResult
case object NotFound extends RulebookResult

object RulebookFinder:
  def listAll(root: Path): List[String] =
    if !Files.isDirectory(root) then List.empty
    else Files.list(root).iterator().asScala
      .filter(Files.isDirectory(_))
      .map(_.getFileName.toString)
      .toList
      .sorted

  def resolve(prefix: String, root: Path): RulebookResult =
    if !Files.isDirectory(root) then return NotFound
    val candidates = Files.list(root).iterator().asScala
      .filter(Files.isDirectory(_))
      .map(_.getFileName.toString)
      .filter(_.startsWith(prefix))
      .toList
    candidates match
      case Nil      => NotFound
      case n :: Nil => Found(n, root.resolve(n).toAbsolutePath.normalize())
      case many     => Ambiguous(many)
