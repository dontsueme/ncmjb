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

public class MPlayerExternRenderLoopBinding extends MPlayerSharedMemory{
	
	public enum FrameState {
		available,
		none
	}
	
	private FrameState state;
	private final BufferMode mode;

	public MPlayerExternRenderLoopBinding(BufferMode bmode) throws MPlayerException {
		super(bmode);
		
		mode = bmode;		
		state = FrameState.none;
	}

	@Override
	public void update() {		
		state = FrameState.available;
		if (mode.equals(BufferMode.Double)) {
			do{
				try {			
					wait();			
				} catch (InterruptedException ignore) {} 
			} while (state.equals(FrameState.available));	
		}
	}
	
	public synchronized void next() {
		state = FrameState.none;
		this.notify();
	}
	
	public FrameState getState() {
		return state;
	}

}
