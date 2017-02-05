load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
)

gerrit_plugin(
    name = "quota",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/resources/**/*"]),
    manifest_entries = [
        "Gerrit-PluginName: quota",
        "Gerrit-Module: com.googlesource.gerrit.plugins.quota.Module",
    ],
)

junit_tests(
    name = "quota_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["quota"],
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":quota__plugin",
    ],
)
