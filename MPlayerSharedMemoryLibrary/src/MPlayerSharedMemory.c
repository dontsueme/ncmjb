/**
 * Author: Stefan Giermair ( zstegi@gmail.com )
 *
 * This file is part of ncmjb.
 * ncmjb is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ncmjb is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ncmjb.  If not, see <http://www.gnu.org/licenses/>.
 */

#include "../../sharedposixipcconfig.c"
#include "mplayeripc_MPlayerSharedMemory.h"

void stop(jlong ptr) {
	struct sharedmplayer *smp = (struct sharedmplayer*)(ptr);
	smp->run = 0;
	uinitipcdata(smp);
	uinitipcsetting(smp);
	free(smp);
}

JNIEXPORT jlong JNICALL Java_mplayeripc_MPlayerSharedMemory_init
  (JNIEnv *env, jobject jobj, jint len, jint count, jint id) {

	if (count < 0 || count > 2)
		return -1;

	struct sharedmplayer *smp = malloc(sizeof(struct sharedmplayer));
	initstruct(smp);

	smp->buffercount = count;
	smp->bufferlen = len;

	if (setNames(id, smp) != 0 || initIPC(smp) != 0) {
		if (smp->error[0] != '\0') {
			jfieldID fid;
			jstring jstr;

			jclass cls = (*env)->GetObjectClass(env, jobj);
			fid = (*env)->GetFieldID(env, cls, "JNIErrorMsg", "Ljava/lang/String;");
			if (fid == NULL) { //should never happen
				printf("failed to find field JNIErrorMsg\n");
			}
			jstr = (*env)->NewStringUTF(env, smp->error);
			if (jstr == NULL) {
				printf("Could not create JNIErrorMsg: Out of Memory\n");
			}
			(*env)->SetObjectField(env, jobj, fid, jstr);
		}

		free(smp);

		return -1;
	}

	return (jlong)(smp);
}

JNIEXPORT void JNICALL Java_mplayeripc_MPlayerSharedMemory_start(JNIEnv *env, jobject jobj, jlong ptr) {

	struct sharedmplayer *smp = (struct sharedmplayer*)(ptr);

	jclass cls = (*env)->GetObjectClass(env, jobj);
	jmethodID mid = (*env)->GetMethodID(env, cls, "updateFromJNI", "(D)V");

	if (mid == NULL) { //should never happen
	    printf("method updateFromJNI not found\n");
	}

	waitUntilTriggered(smp);

	while (smp->run == 1) {
		//printf("pts %d \n", *pts);
		(*env)->CallVoidMethod(env, jobj, mid, *smp->pts);

		if (smp->buffercount == 2) {
			smp->pos++;
			if (smp->pos == smp->buffercount) smp->pos = 0;
			*smp->sharedpos = smp->pos;

			unlockBlock(smp);
		}
		waitUntilTriggered(smp);
	}
}

JNIEXPORT void JNICALL Java_mplayeripc_MPlayerSharedMemory_stop(JNIEnv *env, jobject jobj, jlong ptr) {
	stop(ptr);
}

JNIEXPORT jobject JNICALL Java_mplayeripc_MPlayerSharedMemory_getNativeByteBuffer
  (JNIEnv *env, jobject jobj, jint index, jlong ptr) {

	struct sharedmplayer *smp = (struct sharedmplayer*)(ptr);
	return (*env)->NewDirectByteBuffer(env, smp->sharedindex[index], smp->bufferlen);
}

JNIEXPORT jint JNICALL Java_mplayeripc_MPlayerSharedMemory_startShmSettingDeamon
  (JNIEnv * env, jobject jobj, jlong ptr) {

	struct sharedmplayer *smp = (struct sharedmplayer*)(ptr);

	jclass cls = (*env)->GetObjectClass(env, jobj);
	jmethodID mid = (*env)->GetMethodID(env, cls, "setVideoWidthHeightFromJNI", "(IIII)V");

	if (mid == NULL) { //should never happen
		printf("method setVideoWidthHeight not found\n");
		return -1;
	}

	waitForSetting(smp);

	while(smp->run) {
		smp->bufferlen = smp->sharedsetting[0]*smp->sharedsetting[1]*3;
		int ret = resizeShmData(smp);
		if (ret != 0) {
			printf("error in deamon: cannot resize shm\n");
			return ret;
		}
		setPointers(smp);
		if (smp->buffercount == 2)
			*smp->sharedpos = smp->pos;

		(*env)->CallVoidMethod(env, jobj, mid, smp->sharedsetting[0], smp->sharedsetting[1], smp->sharedsetting[2], smp->sharedsetting[3]);

		triggerSettingFinished(smp);
		waitForSetting(smp);
	}

	return 0;
}
