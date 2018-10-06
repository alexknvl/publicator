enablePlugins(PackPlugin)

lazy val common = Seq(
  organization      := "io.steamcraft",
  version           := "0.0.1-SNAPSHOT",
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public"),
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")),
  scalacOptions ++= List(
    "-deprecation",
    "-encoding", "UTF-8",
    "-explaintypes",
    "-Yrangepos",
    "-feature",
    "-Xfuture",
    "-Ypartial-unification",
    "-language:higherKinds",
    "-language:existentials",
    "-language:implicitConversions",
    "-language:experimental.macros",
    "-unchecked",
    "-Yno-adapted-args",
    "-opt-warnings",
    "-Xlint:_,-type-parameter-shadow",
    "-Xsource:2.13",
    "-Ywarn-dead-code",
     "-Ywarn-extra-implicit",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-numeric-widen",
     "-Ywarn-unused:_,-imports",
    "-Ywarn-value-discard",
     "-opt:l:inline",
     "-opt-inline-from:<source>",
    "-Yno-predef"
  ),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
)

lazy val publicator = project.in(file("."))
  .settings(name := "publicator")
  .settings(common:_*)
  .settings(libraryDependencies ++= List(
    "org.ow2.asm" % "asm"      % "6.1.1",
    "org.ow2.asm" % "asm-util" % "6.1.1",
    "org.ow2.asm" % "asm-tree" % "6.1.1",
    "com.google.guava" % "guava" % "26.0-jre",
    "org.typelevel" %% "cats-core" % "1.1.0",
    "org.tpolecat" %% "atto-core" % "0.6.3"))
  .settings(packMain := Map("publicator" -> "io.steamcraft.publicator.App"))