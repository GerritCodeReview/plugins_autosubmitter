load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS", "PLUGIN_TEST_DEPS")
load("//tools/bzl:classpath.bzl", "classpath_collector")

classpath_collector(
    name = "main_classpath_collect",
    deps = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        "//:autosubmitter__plugin",
    ],
)
