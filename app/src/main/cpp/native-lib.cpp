#include <jni.h>
#include <string>
#include <vector>
#include <unistd.h>
#include <pthread.h>
#include <android/log.h>
#include <cstring>
#include <node.h>

int pipe_stdout[2];
int pipe_stderr[2];
int pipe_stdin[2];
JavaVM* g_vm = nullptr;
jobject g_callback_obj = nullptr;
jmethodID g_on_output_method = nullptr;

void sendToKotlin(const char* prefix, const char* buffer, ssize_t len) {
    if (!g_vm || !g_callback_obj || !g_on_output_method) return;
    
    JNIEnv* env;
    g_vm->AttachCurrentThread(&env, nullptr);
    
    std::string str(buffer, len);
    std::string full_str = std::string(prefix) + str;
    
    jstring jstr = env->NewStringUTF(full_str.c_str());
    env->CallVoidMethod(g_callback_obj, g_on_output_method, jstr);
    env->DeleteLocalRef(jstr);
    
    g_vm->DetachCurrentThread();
}

void* stdout_thread_func(void*) {
    char buffer[1024];
    while (true) {
        ssize_t n = read(pipe_stdout[0], buffer, sizeof(buffer) - 1);
        if (n > 0) {
            sendToKotlin("STDOUT:", buffer, n);
        } else break;
    }
    return nullptr;
}

void* stderr_thread_func(void*) {
    char buffer[1024];
    while (true) {
        ssize_t n = read(pipe_stderr[0], buffer, sizeof(buffer) - 1);
        if (n > 0) {
            sendToKotlin("STDERR:", buffer, n);
        } else break;
    }
    return nullptr;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_NodeBridge_startNodeWithArguments(JNIEnv* env, jobject thiz, jobjectArray args) {
    env->GetJavaVM(&g_vm);
    if (g_callback_obj) {
        env->DeleteGlobalRef(g_callback_obj);
    }
    g_callback_obj = env->NewGlobalRef(thiz);
    
    jclass cbClass = env->GetObjectClass(g_callback_obj);
    g_on_output_method = env->GetMethodID(cbClass, "onOutput", "(Ljava/lang/String;)V");

    pipe(pipe_stdout);
    pipe(pipe_stderr);
    pipe(pipe_stdin);

    dup2(pipe_stdout[1], STDOUT_FILENO);
    dup2(pipe_stderr[1], STDERR_FILENO);
    dup2(pipe_stdin[0], STDIN_FILENO);

    pthread_t thread_out, thread_err;
    pthread_create(&thread_out, nullptr, stdout_thread_func, nullptr);
    pthread_create(&thread_err, nullptr, stderr_thread_func, nullptr);

    int argc = env->GetArrayLength(args);
    std::vector<char*> argv(argc);
    for (int i = 0; i < argc; i++) {
        jstring str = (jstring)env->GetObjectArrayElement(args, i);
        const char* chars = env->GetStringUTFChars(str, nullptr);
        argv[i] = strdup(chars);
        env->ReleaseStringUTFChars(str, chars);
    }

    int result = node::Start(argc, argv.data());

    for (int i = 0; i < argc; i++) {
        free(argv[i]);
    }
    
    // Cleanup pipes? This runs synchronously and blocks, so once node::Start finishes, 
    // we can clean up if we want, but node::Start might crash or exit process entirely.
    
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_NodeBridge_sendInput(JNIEnv* env, jobject thiz, jstring input) {
    const char* str = env->GetStringUTFChars(input, nullptr);
    write(pipe_stdin[1], str, strlen(str));
    write(pipe_stdin[1], "\n", 1);
    env->ReleaseStringUTFChars(input, str);
}
