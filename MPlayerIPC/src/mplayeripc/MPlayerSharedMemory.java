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

package mplayeripc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import mplayeripc.MPlayerException.Cause;

public abstract class MPlayerSharedMemory implements Runnable {
	
	private static File loadRessource(String res) {
		File ressource;
		try {
			InputStream is = MPlayerSharedMemory.class.getResourceAsStream("/"+res);
			ressource = File.createTempFile(res, "");
			FileOutputStream fos = new FileOutputStream(ressource);
			byte[] array = new byte[1024];
			for(int i=is.read(array); i!=-1; i=is.read(array))
				fos.write(array, 0, i);
			fos.close();
			is.close();
		} catch (Exception e) {
			return null;
		}
		return ressource;
	}
	
	public static File loadMplayer() {
		File mplayer = loadRessource("mplayer");			
		if (mplayer != null)
			mplayer.setExecutable(true);
		return mplayer;
	}
	
	static {
		try {
			File library = loadRessource("libMPlayerSharedMemory.so");
			System.load(library.getAbsolutePath());
			library.delete();
		} catch (Exception e) {
			//e.printStackTrace();
			System.out.println("Ressource libMPlayerSharedMemory.so not found. Try to find it on the system.");
			System.loadLibrary("MPlayerSharedMemory");
		}	    
	}
	
	public enum BufferMode {
		Off(1),
		Double(2);
		
		private final int mode;		
		
		BufferMode(int m) {
			mode = m;
		}
		
		private int getValue() {
			return mode;
		}
	}
	
	private final long ptr;
	private final ByteBuffer[] bbar;	
	private final int count;
	private final int id;
	private   volatile int pos = 0;	
	protected volatile double pts = 0;
	protected volatile int width = 0;
	protected volatile int height = 0;
	protected volatile int dwidth = 0;
	protected volatile int dheight = 0;
	protected volatile int len = 0;	
	protected volatile boolean vnewsize = false;	
	private volatile boolean closed = false;
	
	private String JNIErrorMsg = "no errormessage"; // don't take it literally. is set from the c-side
		
	protected MPlayerSharedMemory(BufferMode mode) throws MPlayerException {		
		count = mode.getValue();				
		bbar = new ByteBuffer[count];
		  
		if (count == 2)
			pos = 1;
		
		int tid = new Random().nextInt((int)Math.pow(2, 31));	
		long tptr = init(len, count, tid);
		
		if (tptr == -1){ // collisions should never happen
			closed = true;
			throw new MPlayerException(Cause.MplayerSharedMemoryNotInitializeable, JNIErrorMsg);
		}
		
		id = tid;
		ptr = tptr;
		
		System.out.println("shm id: " + id);		
		
		new Thread() {	
			public void run() { startShmSettingDeamon(ptr); close(); } //TODO: throw exception
		}.start();
		
		setByteBuffers(); 		
	}
	
	private void setByteBuffers() {
		for (int index = 0; index < count; index++){
			ByteBuffer bytebuffer = getNativeByteBuffer(index, ptr);
			bytebuffer.order(ByteOrder.LITTLE_ENDIAN);
			bbar[index] = bytebuffer;
		}
	}
	
	public void run() {	
		//System.out.println("mplayer start");
		if (ptr != -1 && !closed) {
			start(ptr);
		} else {
			//System.out.println("mplayer start failed");
		}
	}
	
	@SuppressWarnings("unused")
	private synchronized void updateFromJNI(double pts) {
		//this.pts = Math.rint(pts*1000000)/1000000;
		this.pts = pts;
		//System.out.println("jpts: "+ this.pts);
		if (count == 2) {
			pos++;		
			if (pos == count) pos = 0;
		}		
		update();
	}
	
	public abstract void update();
	
	private native long init(int bufferlength, int count, int id);
	
	private native ByteBuffer getNativeByteBuffer(int index, long ptr);
	
	private native void start(long ptr);
	
	private native void stop(long ptr);
	
	@SuppressWarnings("unused")
	private synchronized void setVideoWidthHeightFromJNI(int width, int height, int dwidth, int dheight) {
		this.width = width; 
		this.height = height;		
		this.dwidth = dwidth;
		this.dheight = dheight;
		setByteBuffers();
		vnewsize = true;
		this.len = width*height*3;
		System.out.println("java: videowidth: " + width + "/" + dwidth + " videoheight: " + height + "/" + dheight);
	}
	
	private native int startShmSettingDeamon(long ptr);	
	
	public ByteBuffer getCurrentByteBuffer() {	
		return bbar[pos];
	}	
	
	public double getPts() {
		return pts;
	}
	
	public int getVideoWidth() {
		return width;
	}
	
	public int getVideoHeight() {
		return height;
	}
	
	public int getDWidth() {
		return dwidth;
	}
	
	public int getDHeight() {
		return dheight;
	}	
	
	public int getBufferCount() {
		return count;
	}	
	
	public int getID() {
		return id;
	}
	
	public synchronized void close() {
		if (!closed) {
			stop(ptr);
			closed = true;
		}
	}
}
