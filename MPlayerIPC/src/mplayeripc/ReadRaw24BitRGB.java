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

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import mplayeripc.MPlayerControl.Pausing;
import mplayeripc.MPlayerSharedMemory.BufferMode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;

public class ReadRaw24BitRGB {

	public static void main(String[] args) throws Exception {
		
		System.setProperty("sun.java2d.xrender", "True");
		//-Dsun.java2d.xrender=True
		
		System.out.println(System.getProperty("java.version"));
		System.out.println(System.getProperty("java.library.path"));
		System.out.println(System.getProperty("java.vendor"));
		
		//System.runFinalizersOnExit(true);
		
		final String file = "big_buck_bunny_480p_h264.mov";
		final String mplayerLocation = "mplayer-build/mplayer/mplayer";
		
		/*MplayerMediaAnalyzer mma = new MplayerMediaAnalyzer(mplayerLocation, file2);
				
		final int width = mma.getVideoWidth();
		final int height = mma.getVideoHeight();
		System.out.println("video: width " + width + " height " + height);
		
		mma = null;*/
		
		final MPlayerBufferedImage mbi = new MPlayerBufferedImage(BufferMode.Double);
			
		final JFrame frame = new JFrame() {			
			public void paint(Graphics g) {				
				//super.paint(g);	
				mbi.componentResized();
				mbi.update();				
			}
		};
		
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setPreferredSize(new Dimension(848,360));
		frame.pack();		
		frame.setVisible(true);
		frame.createBufferStrategy(2);		
		frame.getContentPane().setBackground(Color.BLACK);
		
		mbi.setComponent(frame.getContentPane());
		new Thread(mbi).start();		
		
		//mbi.scaleVideo(false);
		//mbi.doAspectCorrection(false);
		//mbi.centerHorizontal(false);
		//mbi.centerVertical(false);
		//mbi.setVideoPosition(50, 50);
		
		MPlayerControl mpc = new MPlayerControl(mplayerLocation, null, mbi);
		System.out.println("FIRST pause: "+mpc.isPaused());
		mpc.play(file);	
		/*System.out.println("Filename: " + mpc.getFileName());
		System.out.println("return width: " + mpc.getDisplayWidth() + " height: " + mpc.getDisplayHeight());
		System.out.println("Metadata: " + mpc.getMetadata());
		System.out.println("AudioCodeName: "+ mpc.getAudioCodec());

		System.out.println("return aspect: "+mpc.getVideoAspect());
		mpc.setTimePos(5);
		mpc.seek(5, 0, Pausing.toogle);
		System.out.println("pause: "+mpc.isPaused());
		mpc.pause();
		System.out.println("pause: "+mpc.isPaused());
		System.out.println("return length: " +mpc.getLength());
		System.out.println("timepos: " + mpc.getTimePos());*/
		Thread.sleep(50000);

		mpc.quit();
		mbi.close();
		
		/*MplayerControl mpc2 = new MplayerControl("mplayer", null, null);
		
		mpc2.play("test.mp4");
		System.out.println("Metadata: " + mpc2.getMetadata());
		Thread.sleep(10000);
		System.out.println("return width: " + mpc2.getDisplayWidth() + " height: " + mpc2.getDisplayHeight());

		mpc2.quit();*/
	}
}
