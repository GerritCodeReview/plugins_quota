load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    maven_jar(
        name = "commons-lang3",
        artifact = "org.apache.commons:commons-lang3:3.17.0",
        sha1 = "b17d2136f0460dcc0d2016ceefca8723bdf4ee70",
    )
