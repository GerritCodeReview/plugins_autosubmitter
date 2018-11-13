load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS", "PLUGIN_TEST_DEPS", "gerrit_plugin")
load("//tools/bzl:junit.bzl", "junit_tests")

gerrit_plugin(
    name = "autosubmitter",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: autosubmitter",
        "Gerrit-Module: com.criteo.gerrit.plugins.automerge.AutomergeModule",
    ],
    resources = glob(["src/main/resources/*"]),
)

junit_tests(
    name = "test",
    testonly = 1,
    srcs = glob(["src/test/java/**/*.java"]),
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":autosubmitter",
    ],
)
