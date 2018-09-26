load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "quota",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: quota",
        "Gerrit-Module: com.googlesource.gerrit.plugins.quota.Module",
    ],
    resources = glob(["src/main/resources/**/*"]),
)

junit_tests(
    name = "quota_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["quota"],
    deps = [
        ":quota__plugin_test_deps",
    ],
)

java_library(
    name = "quota__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":quota__plugin",
        "@mockito//jar",
    ],
)
