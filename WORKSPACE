workspace(name = "quota")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "e3a8273dbfced5c41f6f08f49d063c4366be0278",
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
