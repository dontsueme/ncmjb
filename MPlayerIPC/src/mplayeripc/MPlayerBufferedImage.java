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

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.HashMap;

import sun.awt.image.ByteInterleavedRaster;

public class MPlayerBufferedImage extends MPlayerSharedMemory {
	
	// -Dsun.java2d.opengl=True 
	
	static private boolean hwaccel = false;
	
	static {
		if (Boolean.getBoolean("sun.java2d.opengl") || 
				Boolean.getBoolean("sun.java2d.xrender") && System.getProperty("java.version").startsWith("1.7"))
			hwaccel = true;
	}
	
	final private HashMap<RenderingHints.Key, Object> hints = new HashMap<RenderingHints.Key, Object>();
	
	private BufferedImage img;
	private ByteInterleavedRaster raster; 
	private byte[] data;
	private Graphics2D g;
	private Component component = null;	
	private ByteBuffer current;
	
	private double aspectcorrection = 0;
	private double scalerx, scalery;	
	private int posx = 0, posy = 0;
	
	private volatile boolean componentresized = true;
	private volatile boolean doscale = true;
	private volatile boolean acflag = true;
	private volatile boolean centerh = true;
	private volatile boolean centerv = true;
	private volatile int userposx = 0;
	private volatile int userposy = 0;
	
	final private Toolkit toolkit;
	
	public MPlayerBufferedImage (BufferMode mode) throws MPlayerException {
		super(mode);
		toolkit = Toolkit.getDefaultToolkit();
		if (hwaccel) {
			hints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		} else {
			hints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
	        hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		}
	}
	
	private void calcScaler() {		
		double scaler; 
		if (doscale) {
			scaler = Math.min(1.0/(width==dwidth?width:(acflag?dwidth:width))*component.getWidth(), 1.0/height*component.getHeight());
		} else {
			scaler = 1;
		}
		scalerx = width==dwidth?scaler:scaler*aspectcorrection;
		scalery = scaler;		
	}
	
	@Override
	public synchronized void update() {		
		if (len != 0 && component != null) {
			if (vnewsize) {
				img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);	
				raster = (ByteInterleavedRaster)img.getRaster();
				data = raster.getDataStorage();
				aspectcorrection = acflag?1.0/width*dwidth:1;
				calcScaler();
				
				vnewsize = false;
				componentresized = true;
			}
	
			current = getCurrentByteBuffer();
			current.rewind();
			current.get(data);
			
			//raster.markDirty(); 					// works only with openjdk
			raster.putByteData(0, 0, 1, 1, data); 	// workaround for (SUNs-R.I.P.)Oracles JDK/JRE6
			
			g = (Graphics2D) component.getGraphics();
			g.setRenderingHints(hints);
			if (componentresized) {
				calcScaler();
				final double twidth = scalerx*width;
				final double theight = scalery*height;

				final int componentwidth = component.getWidth();
				final int componentheight = component.getHeight();
				
				g.setBackground(component.getBackground());
				
				//TODO: do it properly
				if (centerh && centerv && userposx == 0 && userposy == 0) {								
					g.clearRect(0, 0, componentwidth, (int)((componentheight-theight)/2)+1); 				//clear upper area				
					g.clearRect(0, (int)((componentheight+theight)/2)-1, componentwidth, componentheight); 	//clear bottom area
					g.clearRect(0, 0, (int)((componentwidth-twidth)/2)+1, componentheight); 				//clear left area
					g.clearRect((int)((componentwidth+twidth)/2)-1, 0, componentwidth, componentheight); 	//clear right area
				} else {
					g.clearRect(0, 0, componentwidth, componentheight);
				}
				
				posx = !centerv?userposx:(int)((componentwidth-twidth)/2/(doscale?scalerx:1));
				posy = !centerh?userposy:(int)((componentheight-theight)/2/(doscale?scalery:1));
				
				componentresized = false;
			}
			g.scale(scalerx, scalery);
			g.drawImage(img, posx, posy, null);
			g.dispose();
			
			toolkit.sync(); //if BufferStrategy created AND java2d-opengl-pipe is used or you use a wm like compiz, you get vsync for free
		}
	}
	
	public synchronized void setComponent(Component comp) {
		component = comp;
	}
	
	public void finalize(){
		close();
	}
	
	public void componentResized() {
		componentresized = true;
	}
	
	public void scaleVideo(boolean doscale) {
		this.doscale = doscale;
		componentResized();
	}
	
	public void doAspectCorrection(boolean aspect) {
		acflag = aspect;
		componentResized();
	}
	
	public void centerHorizontal(boolean centerh) {
		this.centerh = centerh;
		componentResized();
	}
	
	public void centerVertical(boolean centerv) {
		this.centerv = centerv;
		componentResized();
	}
	
	public void unsetVideoPosition() {
		setVideoPosition(0, 0);
	}
	
	public void setVideoPosition(int x, int y) {
		userposx = x;
		userposy = y;
		componentResized();
	}
}
