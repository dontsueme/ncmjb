/*
 * modified TARGA video output
 *
 *    thanks to Daniele Forghieri ( guru@digitalfantasy.it )
 *    modified by Stefan Giermair ( zstegi@gmail.com )
 *
 * This file is part of MPlayer.
 *
 * MPlayer is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * MPlayer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with MPlayer; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#include "../../../ncmjb/sharedposixipcconfig.c"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <math.h>

#include "subopt-helper.h"
#include "config.h"
#include "mp_msg.h"
#include "video_out.h"
#include "video_out_internal.h"

struct sharedmplayer smp;
int first = 1;
int stride = 0;

static const vo_info_t info = { "SharedMem Output", "tga", "", "" };

const LIBVO_EXTERN (tga)

static uint32_t draw_image(mp_image_t* mpi) {

	if (smp.buffercount == 2) {
		block(&smp);
	}
	*smp.pts = global_vo->next_pts; //TODO: get pts in svn-version
	trigger(&smp);

	return VO_TRUE;
}

static int config(uint32_t width, uint32_t height, uint32_t d_width,
		uint32_t d_height, uint32_t flags, char *title, uint32_t format) {
	vo_directrendering = 1;

	smp.bufferlen = height * width * 3;
	stride = width * 3;

	int ipcinit;
	if (first && (ipcinit = initIPCSetting(&smp)) != 0)
		return ipcinit;

	((int*) smp.sharedsetting)[0] = (int) width;
	((int*) smp.sharedsetting)[1] = (int) height;
	((int*) smp.sharedsetting)[2] = (int) d_width;
	((int*) smp.sharedsetting)[3] = (int) d_height;

	triggerNewSetting(&smp);
	waitUntilSettingFinished(&smp);

	if ((ipcinit = initIPCDataShm(&smp)) != 0)
		return ipcinit;

	if (first && (ipcinit = initIPCDataSems(&smp)) != 0)
		return ipcinit;

	setPointers(&smp);
	if (first && smp.buffercount == 2) {
		smp.pos = *smp.sharedpos;
	}

	if (first)
		first = 0;

	return ipcinit;
}

static void draw_osd(void) {
}

static void flip_page(void) {
	return;
}

static int draw_slice(uint8_t *srcimg[], int stride[], int w, int h, int x, int y) {
	return 0;
}

static int draw_frame(uint8_t * src[]) {
	return -1;
}

static int query_format(uint32_t format) {

	/*int caps = VFCAP_CSP_SUPPORTED | VFCAP_CSP_SUPPORTED_BY_HW |
	 VFCAP_FLIP |
	 VFCAP_HWSCALE_UP | VFCAP_HWSCALE_DOWN | VFCAP_ACCEPT_STRIDE;*/
	//return caps;
	switch (format) {
	//case IMGFMT_RGB|24:
	case IMGFMT_BGR | 24:
		return VFCAP_CSP_SUPPORTED | VFCAP_CSP_SUPPORTED_BY_HW | VOCAP_NOSLICES;// | VFCAP_TIMER;
	}
	return 0;
}

static void uninit(void) {
}

static void check_events(void) {
}

static int preinit(const char *arg) {
	int id = 0;
	const opt_t subopts[] = {
	{ "id",          OPT_ARG_INT,  &id,  NULL},
	{ "mode", OPT_ARG_INT, &smp.buffercount, NULL },
	{ NULL } };

	if (subopt_parse(arg, subopts) != 0) {
		//mp_msg(MSGT_VO, MSGL_WARN, MSGTR_LIBVO_TGA_UnknownSubdevice, arg);
		mp_tmsg(MSGT_VO,MSGL_WARN, "[VO_TGA] Unknown subdevice: %s.\n",arg);
		return ENOSYS;
	}

	if (smp.buffercount < 1 || smp.buffercount > 2 || id < 0) { //Todo: check if shm_setting_id exists
		return -1;
	}
	printf("buffercount: %d  id: %d\n", smp.buffercount, id);

	initstruct(&smp);
	setNames(id, &smp);

	return 0;
}

static uint32_t get_image(mp_image_t *mpi) {
	/*if (mpi->flags & MP_IMGFLAG_READABLE) return VO_FALSE;
	 if (mpi->type != MP_IMGTYPE_STATIC && mpi->type != MP_IMGTYPE_TEMP &&
	 (mpi->type != MP_IMGTYPE_NUMBERED || mpi->number))
	 return VO_FALSE;*/

	mpi->stride[0] = stride;
	mpi->planes[0] = smp.sharedindex[smp.pos];

	if (smp.buffercount == 2) {
		smp.pos++;
		if (smp.pos == smp.buffercount)
			smp.pos = 0;
	}

	mpi->flags |= MP_IMGFLAG_DIRECT;
	return VO_TRUE;
}

// svn
//static int control(uint32_t request, void *data, ...)
// uau-fork
static int control(uint32_t request, void *data) {
	switch (request) {
	case VOCTRL_DRAW_IMAGE:
		return draw_image(data);

	case VOCTRL_QUERY_FORMAT:
		return query_format(*((uint32_t*) data));

	case VOCTRL_GET_IMAGE:
		return get_image(data);
	}
	return VO_NOTIMPL;
}
