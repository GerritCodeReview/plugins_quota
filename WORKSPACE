workspace(name = "quota")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "69ae6ee516ec1cc51d0a07fe4419a325eb9063ce",
    #local_path = "/home/<user>/projects/bazlets",
)

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()

# Load snapshot Plugin API
#gerrit_api(version = "3.0.xy-SNAPSHOT")
