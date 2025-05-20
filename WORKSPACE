workspace(name = "quota")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "a52e3f381e2fe2a53f7641150ff723171a2dda1e",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# specify version with `-SNAPSHOT` prefix to pull from local repo
gerrit_api()
