workspace(name = "quota")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "2d0cc7ee675fc979594fa29c21418b921adbad68",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# specify version with `-SNAPSHOT` prefix to pull from local repo
# example: gerrit_api(version = "3.3.0-SNAPSHOT")
gerrit_api()
