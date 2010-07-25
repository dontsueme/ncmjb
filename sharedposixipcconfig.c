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

#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <stdlib.h>

#include <semaphore.h>
#include <fcntl.h>           /* For O_* constants */
#include <sys/mman.h>
#include <unistd.h>
#include <sys/types.h>

#include <stdint.h>

//Todo: remove duplicate code, i'm to lazy at the moment

#define SHM "/ncmjb_shm_data_"
#define SEM_LOCK "/ncmjb_lock_"
#define SEM_TRIGGER "/ncmjb_trigger_"

#define SHM_SETTING "/ncmjb_shm_setting_"
#define SEM_DOSETTING "/ncmjb_dosetting_"
#define SEM_SETTINGFINISHED "/ncmjb_settingfinished_"

#define IDSIZE 11 // idsize +1
#define IDFORMATSTRING "%010d"

#define SHM_SIZE strlen(SHM)+IDSIZE
#define SEM_LOCK_SIZE strlen(SEM_LOCK)+IDSIZE
#define SEM_TRIGGER_SIZE strlen(SEM_TRIGGER)+IDSIZE

#define SHM_SETTING_SIZE strlen(SHM_SETTING)+IDSIZE
#define SEM_DOSETTING_SIZE strlen(SEM_DOSETTING)+IDSIZE
#define SEM_SETTINGFINISHED_SIZE strlen(SEM_SETTINGFINISHED)+IDSIZE

struct sharedmplayer {
	int run; // = 1;

	uint8_t *shared;
	uint8_t *sharedindex[2];
	int *sharedsetting;
	int *sharedpos;
	double *pts;

	int pos; // = 0;
	int buffercount;
	int bufferlen;

	sem_t 	*sem_lock, *sem_trigger,
			*sem_dosetting_trigger, *sem_setting_finished_trigger;

	char 	sem_lock_name[SEM_LOCK_SIZE], sem_trigger_name[SEM_TRIGGER_SIZE], shm_name[SHM_SIZE],
			sem_dosetting_trigger_name[SEM_DOSETTING_SIZE],
			sem_setting_finished_trigger_name[SEM_SETTINGFINISHED_SIZE],
			shm_setting_name[SHM_SETTING_SIZE];

	char error[100];
};

void initstruct(struct sharedmplayer *smp) {
	smp->run = 1;
	smp->pos = 0;

	smp->sem_lock_name[0] = '\0';
	smp->sem_trigger_name[0] = '\0';
	smp->shm_name[0] = '\0';
	smp->sem_dosetting_trigger_name[0] = '\0';
	smp->sem_setting_finished_trigger_name[0] = '\0';
	smp->shm_setting_name[0] = '\0';

	smp->shared = NULL;
	smp->sharedsetting = NULL;
	smp->sharedindex[0] = NULL;
	smp->sharedindex[1] = NULL;
	smp->sharedpos = NULL;
	smp->pts = NULL;

	smp->error[0] = '\0';
}

int setNames(int id, struct sharedmplayer *smp) {
	if (id < 0)
		return -1;

	//printf("id %d", id);
	char temp[IDSIZE];
	sprintf(temp, IDFORMATSTRING, id);
	//printf(" converted id %s length %d", temp, strlen(temp));

	strcat(smp->sem_lock_name, SEM_LOCK);
	strcat(smp->sem_lock_name, temp);
	strcat(smp->sem_trigger_name, SEM_TRIGGER);
	strcat(smp->sem_trigger_name, temp);
	strcat(smp->shm_name, SHM);
	strcat(smp->shm_name, temp);

	strcat(smp->sem_dosetting_trigger_name, SEM_DOSETTING);
	strcat(smp->sem_dosetting_trigger_name, temp);
	strcat(smp->sem_setting_finished_trigger_name, SEM_SETTINGFINISHED);
	strcat(smp->sem_setting_finished_trigger_name, temp);
	strcat(smp->shm_setting_name, SHM_SETTING);
	strcat(smp->shm_setting_name, temp);

	return 0;
}

void setPointers(struct sharedmplayer *smp) {
	smp->sharedindex[0] = smp->shared;
	int ptslocation = smp->bufferlen * smp->buffercount;
	if (smp->buffercount == 2) {
		smp->sharedindex[1] = smp->shared + smp->bufferlen;
		ptslocation += sizeof(int);
		smp->sharedpos = (int*)(smp->shared + smp->bufferlen * smp->buffercount);
	}
	smp->pts = (double*)(smp->shared + ptslocation);
}

int calcShmSize(struct sharedmplayer *smp) {
	int sharedmemsize = smp->bufferlen * smp->buffercount + sizeof(double);
	if (smp->buffercount == 2)
		sharedmemsize += sizeof(int);
	return sharedmemsize;
}

void uinitipcdata(struct sharedmplayer *smp) {
	sem_unlink(smp->sem_trigger_name);
	sem_unlink(smp->sem_lock_name);
	shm_unlink(smp->shm_name);
}

void uinitipcsetting(struct sharedmplayer *smp) {
	sem_unlink(smp->sem_dosetting_trigger_name);
	sem_unlink(smp->sem_setting_finished_trigger_name);
	shm_unlink(smp->shm_setting_name);
}

int createShm(void **shmptr, char *shmname, int shmsize, int shmopenflags, char *error) {
	int fd = shm_open(shmname, shmopenflags, 0600);
	if (fd == -1) {
		printf("Error posix-shm_open: %s %s\n", shmname, strerror(errno));
		sprintf(error, "Error posix-shm_open: %s %s\n", shmname, strerror(errno));
		return -1;
	}

	if (shmopenflags & O_CREAT) {
		if (ftruncate(fd, shmsize) == -1) {
			shm_unlink(shmname);
			printf("Error ftruncate: %d %s\n", shmsize, strerror(errno));
			sprintf(error, "Error ftruncate: %d %s\n", shmsize, strerror(errno));
			return -1;
		}
	}

	*shmptr = mmap(0, shmsize, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);

	if (*shmptr == NULL) {
		shm_unlink(shmname);
		printf("Error posix-mmap: %s %s\n", shmname, strerror(errno));
		sprintf(error, "Error posix-mmap: %s %s\n", shmname, strerror(errno));
		return -1;
	}
	return 0;
}

int createSems(sem_t **sem1, char *semname1, sem_t **sem2, char *semname2, char* error) {
	*sem1 = sem_open(semname1, O_EXCL | O_CREAT, 0600, 0);
	if (*sem1 == SEM_FAILED) {
		printf("Error posix-sem_open: %s %s\n", semname1, strerror(errno));
		sprintf(error, "Error posix-sem_open: %s %s\n", semname1, strerror(errno));
		return -1;
	}

	*sem2 = sem_open(semname2, O_EXCL | O_CREAT, 0600, 0);
	if (*sem2 == SEM_FAILED) {
		sem_unlink(semname1);
		printf("Error posix-sem_open: %s %s\n", semname2, strerror(errno));
		sprintf(error, "Error posix-sem_open: %s %s\n", semname2, strerror(errno));
		return -1;
	}
	return 0;
}

int getSem(sem_t **sem, char *semname, char *error) {
	*sem = sem_open(semname, 0);
	if (*sem == SEM_FAILED) {
		printf("Error posix-sem_open: %s %s\n", semname, strerror(errno));
		sprintf(error, "Error posix-sem_open: %s %s\n", semname, strerror(errno));
		return -1;
	}
	return 0;
}

int initIPCSetting(struct sharedmplayer *smp) {
		int ret;
		if ((ret = createShm((void*)&smp->sharedsetting, smp->shm_setting_name, sizeof(int) * 4, O_RDWR, smp->error)) != 0)
			return ret;
		if ((ret = getSem(&smp->sem_dosetting_trigger, smp->sem_dosetting_trigger_name, smp->error)) != 0)
			return ret;
		if ((ret = getSem(&smp->sem_setting_finished_trigger, smp->sem_setting_finished_trigger_name, smp->error)) != 0)
			return ret;
	return 0;
}

int initIPCDataShm(struct sharedmplayer *smp) {
	int ret;

	if ((ret = createShm((void*)&smp->shared, smp->shm_name, calcShmSize(smp), O_RDWR, smp->error)) != 0)
		return ret;

	return 0;
}

int initIPCDataSems(struct sharedmplayer *smp) {
	int ret;
	if ((ret = getSem(&smp->sem_lock, smp->sem_lock_name, smp->error)) != 0)
		return ret;
	if ((ret = getSem(&smp->sem_trigger, smp->sem_trigger_name, smp->error)) != 0)
		return ret;

	int temp;
	sem_getvalue(smp->sem_lock, &temp);
	//printf("sem_lock: %d\n", temp);
	if (temp == 0)
		sem_post(smp->sem_lock);

	return 0;
}

void block(struct sharedmplayer *smp) {
	sem_wait(smp->sem_lock);
}

void trigger(struct sharedmplayer *smp) {
	sem_post(smp->sem_trigger);
}

void triggerNewSetting(struct sharedmplayer *smp) {
	sem_post(smp->sem_dosetting_trigger);
}

void waitUntilSettingFinished(struct sharedmplayer *smp) {
	sem_wait(smp->sem_setting_finished_trigger);
}

int initIPC(struct sharedmplayer *smp) {
	int clear = 0;
	int ret = 0;

	ret = createShm((void*)&smp->shared, smp->shm_name, calcShmSize(smp), O_EXCL | O_CREAT | O_RDWR, smp->error);

	if (ret == 0) {
		ret = createSems(&smp->sem_lock, smp->sem_lock_name,
				&smp->sem_trigger, smp->sem_trigger_name, smp->error);
		clear = 2;
	}
	if (ret == 0) {
		ret = createShm((void*)&smp->sharedsetting, smp->shm_setting_name, sizeof(int)*4, O_EXCL | O_CREAT | O_RDWR, smp->error);
		clear = 3;
	}
	if (ret == 0) {
		ret = createSems(&smp->sem_dosetting_trigger, smp->sem_dosetting_trigger_name,
			&smp->sem_setting_finished_trigger, smp->sem_setting_finished_trigger_name, smp->error);
		clear = 4;
	}

	if (ret != 0) {
		if (clear == 2) {
			shm_unlink(smp->shm_name);
		} else if (clear == 3) {
			uinitipcdata(smp);
		} else if (clear == 4) {
			uinitipcdata(smp);
			shm_unlink(smp->shm_setting_name);
		}
		return -1;
	}

	return 0;
}

int resizeShmData(struct sharedmplayer *smp) {
	return createShm((void*)&smp->shared, smp->shm_name, calcShmSize(smp), O_CREAT | O_RDWR, smp->error);
}

void waitUntilTriggered(struct sharedmplayer *smp) {
	sem_wait(smp->sem_trigger);
}

void unlockBlock(struct sharedmplayer *smp) {
	sem_post(smp->sem_lock);
}

void waitForSetting(struct sharedmplayer *smp) {
	sem_wait(smp->sem_dosetting_trigger);
}

void triggerSettingFinished(struct sharedmplayer *smp) {
	sem_post(smp->sem_setting_finished_trigger);
}
