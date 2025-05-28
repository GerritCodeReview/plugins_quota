workspace(name = "quota")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "f930de7dc62e319c82d2afc8f10efd1290fd62b8",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

load(":external_plugin_deps.bzl", "external_plugin_deps")
external_plugin_deps()

# specify version with `-SNAPSHOT` prefix to pull from local repo
# example: gerrit_api(version = "3.3.0-SNAPSHOT")
gerrit_api()
