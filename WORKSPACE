workspace(name = "quota")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "7ff4605f48db148197675a0d2ea41ee07cb72fd3",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# specify version with `-SNAPSHOT` prefix to pull from local repo
gerrit_api()
