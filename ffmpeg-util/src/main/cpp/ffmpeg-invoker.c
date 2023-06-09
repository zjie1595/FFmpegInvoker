#include "ffmpeg-invoker.h"

#include <jni.h>
#include <string.h>
#include "ffmpeg_thread.h"
#include "android_log.h"
#include "cmdutils.h"

static JavaVM *jvm = NULL;
//java虚拟机
static jclass m_clazz = NULL;//当前类(面向java)

/**
 * 回调执行Java方法
 * 参看 Jni反射+Java反射
 */
void callJavaMethod(JNIEnv *env, jclass clazz,int ret) {
    if (clazz == NULL) {
        LOGE("---------------clazz isNULL---------------");
        return;
    }
    //获取方法ID (I)V指的是方法签名 通过javap -s -public FFmpegCmd 命令生成
    jmethodID methodID = (*env)->GetStaticMethodID(env, clazz, "onExecuted", "(I)V");
    if (methodID == NULL) {
        LOGE("---------------methodID isNULL---------------");
        return;
    }
    //调用该java方法
    (*env)->CallStaticVoidMethod(env, clazz, methodID,ret);
}
void callJavaMethodProgress(JNIEnv *env, jclass clazz,float ret) {
    if (clazz == NULL) {
        LOGE("---------------clazz isNULL---------------");
        return;
    }
    //获取方法ID (I)V指的是方法签名 通过javap -s -public FFmpegCmd 命令生成
    jmethodID methodID = (*env)->GetStaticMethodID(env, clazz, "onProgress", "(F)V");
    if (methodID == NULL) {
        LOGE("---------------methodID isNULL---------------");
        return;
    }
    //调用该java方法
    (*env)->CallStaticVoidMethod(env, clazz, methodID,ret);
}

/**
 * c语言-线程回调
 */
static void ffmpeg_callback(int ret) {
    JNIEnv *env;
    //附加到当前线程从JVM中取出JNIEnv, C/C++从子线程中直接回到Java里的方法时  必须经过这个步骤
    (*jvm)->AttachCurrentThread(jvm, (void **) &env, NULL);
    callJavaMethod(env, m_clazz,ret);

    //完毕-脱离当前线程
    (*jvm)->DetachCurrentThread(jvm);
}

void ffmpeg_progress(float percent) {
    JNIEnv *env;
    (*jvm)->AttachCurrentThread(jvm, (void **) &env, NULL);
    callJavaMethodProgress(env, m_clazz,percent);
    (*jvm)->DetachCurrentThread(jvm);
}

JNIEXPORT jint JNICALL
Java_com_github_xch168_ffmpeg_invoker_FFmpegInvoker_exec(JNIEnv *env, jclass clazz, jint cmdnum, jobjectArray cmdline) {
    (*env)->GetJavaVM(env, &jvm);
    m_clazz = (*env)->NewGlobalRef(env, clazz);
    //---------------------------------C语言 反射Java 相关----------------------------------------
    //---------------------------------java 数组转C语言数组----------------------------------------
    int i = 0;//满足NDK所需的C99标准
    char **argv = NULL;//命令集 二维指针
    jstring *strr = NULL;

    if (cmdline != NULL) {
        argv = (char **) malloc(sizeof(char *) * cmdnum);
        strr = (jstring *) malloc(sizeof(jstring) * cmdnum);

        for (i = 0; i < cmdnum; ++i) {//转换
            strr[i] = (jstring)(*env)->GetObjectArrayElement(env, cmdline, i);
            argv[i] = (char *) (*env)->GetStringUTFChars(env, strr[i], 0);
        }

    }
    //---------------------------------java 数组转C语言数组----------------------------------------
    //---------------------------------执行FFmpeg命令相关----------------------------------------
    //新建线程 执行ffmpeg 命令
    ffmpeg_thread_run_cmd(cmdnum, argv);
    //注册ffmpeg命令执行完毕时的回调
    ffmpeg_thread_callback(ffmpeg_callback);

    free(strr);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_github_xch168_ffmpeg_invoker_FFmpegInvoker_exit(JNIEnv *env, jclass type) {
    (*env)->GetJavaVM(env, &jvm);
    m_clazz = (*env)->NewGlobalRef(env, type);
    ffmpeg_thread_cancel();
}

JNIEXPORT jstring JNICALL
Java_com_github_xch168_ffmpeg_invoker_FFmpegInvoker_getConfigInfo(JNIEnv *env, jclass clazz) {
    char info[10000] = {0};
    sprintf(info, "%s\n", avcodec_configuration());
    return (*env)->NewStringUTF(env, info);
}

JNIEXPORT jstring JNICALL
Java_com_github_xch168_ffmpeg_invoker_FFmpegInvoker_getAVCodecInfo(JNIEnv *env, jclass clazz) {
    char info[40000] = { 0 };

    AVCodec *c_temp = av_codec_next(NULL);
    while (c_temp != NULL) {
        if (c_temp->decode != NULL) {
            sprintf(info, "%s[Dec]", info);
        }
        else {
            sprintf(info, "%s[Enc]", info);
        }
        switch (c_temp->type) {
            case AVMEDIA_TYPE_VIDEO:
                sprintf(info, "%s[Video]", info);
                break;
            case AVMEDIA_TYPE_AUDIO:
                sprintf(info, "%s[Audio]", info);
                break;
            default:
                sprintf(info, "%s[Other]", info);
                break;
        }
        sprintf(info, "%s[%10s]\n", info, c_temp->name);


        c_temp=c_temp->next;
    }

    return (*env)->NewStringUTF(env, info);
}

JNIEXPORT jstring JNICALL
Java_com_github_xch168_ffmpeg_invoker_FFmpegInvoker_getAVFormatInfo(JNIEnv *env, jclass clazz) {
    char info[40000] = { 0 };

    AVInputFormat *if_temp = av_iformat_next(NULL);
    AVOutputFormat *of_temp = av_oformat_next(NULL);
    //Input
    while (if_temp != NULL) {
        sprintf(info, "%s[In ][%10s]\n", info, if_temp->name);
        if_temp = if_temp->next;
    }
    //Output
    while (of_temp != NULL) {
        sprintf(info, "%s[Out][%10s]\n", info, of_temp->name);
        of_temp = of_temp->next;
    }

    return (*env)->NewStringUTF(env, info);
}

JNIEXPORT jstring JNICALL
Java_com_github_xch168_ffmpeg_invoker_FFmpegInvoker_getAVFilterInfo(JNIEnv *env, jclass clazz) {
    char info[40000] = { 0 };
    AVFilter *f_temp = (AVFilter *)avfilter_next(NULL);
    while (f_temp != NULL) {
        sprintf(info, "%s[%10s]\n", info, f_temp->name);
        f_temp = f_temp->next;
    }

    return (*env)->NewStringUTF(env, info);
}