#include <android/log.h>
#include <errno.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define LOG_TAG "MaaEndRootLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const char *kAppProcessPath = "/system/bin/app_process";

typedef struct {
    const char *apk_path;
    const char *process_name;
    const char *starter_class;
} LauncherArgs;

static bool starts_with(const char *value, const char *prefix) {
    return strncmp(value, prefix, strlen(prefix)) == 0;
}

static bool parse_args(int argc, char **argv, LauncherArgs *out) {
    memset(out, 0, sizeof(*out));

    for (int i = 1; i < argc; ++i) {
        if (starts_with(argv[i], "--apk=")) {
            out->apk_path = argv[i] + 6;
        } else if (starts_with(argv[i], "--process-name=")) {
            out->process_name = argv[i] + 15;
        } else if (starts_with(argv[i], "--starter-class=")) {
            out->starter_class = argv[i] + 16;
        }
    }

    return out->apk_path != NULL && out->process_name != NULL && out->starter_class != NULL;
}

static char *format_arg(const char *prefix, const char *value) {
    size_t size = strlen(prefix) + strlen(value) + 1;
    char *out = (char *) malloc(size);
    if (out == NULL) {
        return NULL;
    }
    snprintf(out, size, "%s%s", prefix, value);
    return out;
}

int main(int argc, char **argv) {
    LauncherArgs args;
    char *nice_name_arg = NULL;
    char **exec_args = NULL;
    int exec_argc = 0;

    if (!parse_args(argc, argv, &args)) {
        LOGE("Missing required args");
        return 2;
    }

    if (setenv("CLASSPATH", args.apk_path, 1) != 0) {
        LOGE("setenv(CLASSPATH) failed: %s", strerror(errno));
        return 3;
    }

    nice_name_arg = format_arg("--nice-name=", args.process_name);
    if (nice_name_arg == NULL) {
        LOGE("Failed to allocate process name arg");
        return 4;
    }

    exec_args = (char **) calloc((size_t) argc + 4, sizeof(char *));
    if (exec_args == NULL) {
        LOGE("calloc failed: %s", strerror(errno));
        free(nice_name_arg);
        return 5;
    }

    exec_args[exec_argc++] = (char *) kAppProcessPath;
    exec_args[exec_argc++] = "/system/bin";
    exec_args[exec_argc++] = nice_name_arg;
    exec_args[exec_argc++] = (char *) args.starter_class;

    for (int i = 1; i < argc; ++i) {
        if (starts_with(argv[i], "--apk=") ||
            starts_with(argv[i], "--process-name=") ||
            starts_with(argv[i], "--starter-class=")) {
            continue;
        }
        exec_args[exec_argc++] = argv[i];
    }
    exec_args[exec_argc] = NULL;

    LOGI("Launching root app_process with starter=%s", args.starter_class);
    execv(kAppProcessPath, exec_args);

    LOGE("execv(%s) failed: %s", kAppProcessPath, strerror(errno));
    free(exec_args);
    free(nice_name_arg);
    return 6;
}
