workspace(name = "quota")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "0f81174e3d1b892a1342ebc75bb4bbb158ae0efe",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# specify version with `-SNAPSHOT` prefix to pull from local repo
# example: gerrit_api(version = "3.3.0-SNAPSHOT")
gerrit_api()
