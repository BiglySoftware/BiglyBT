/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package com.biglybt.core.disk.impl;

import java.util.Arrays;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoSet;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.util.Debug;

/**
 * @author Aaron Grunthal
 * @create 10.05.2008
 */
public class DiskManagerFileInfoSetImpl implements DiskManagerFileInfoSet {

	final DiskManagerFileInfoImpl[] files;
	final DiskManagerHelper diskManager;

	public DiskManagerFileInfoSetImpl(DiskManagerFileInfoImpl[] files, DiskManagerHelper dm) {
		this.files = files;
		this.diskManager = dm;
	}

	@Override
	public void load(int[] priorities, boolean[] skipped){
		for ( int i=0;i<priorities.length;i++){
			files[i].load( priorities[i], skipped[i] );
		}
	}
	
	@Override
	public DiskManagerFileInfo[] getFiles() {
		return files;
	}

	@Override
	public int nbFiles() {
		return files.length;
	}

	@Override
	public void 
	setPriority(
		int[] newPriorities) 
	{
		if ( newPriorities.length != files.length ){
			
			throw new IllegalArgumentException("array length mismatches the number of files");
		}
		
		DownloadManagerState dmState = diskManager.getDownloadState();

		try{
			dmState.suppressStateSave(true);

			for ( int i=0;i<files.length;i++ ){
				
					// existing code ignored 0, dunno why, removed it. anyway I've added Integer.MIN_VALUE to signify no change
				
				int np = newPriorities[i];
				
				if ( np != Integer.MIN_VALUE ){
					
					files[i].setPriority( np );
				}
			}
		}finally{
			
			dmState.suppressStateSave(false);
		}
	}

	@Override
	public void 
	setSkipped(
		boolean[] toChange, 
		boolean setSkipped) 
	{
		synchronized( DiskManagerUtil.skip_lock ){
		
			if (toChange.length != files.length ){
				
				throw new IllegalArgumentException("array length mismatches the number of files");
			}
			
			DownloadManagerState dmState = diskManager.getDownloadState();
	
			try	{
				dmState.suppressStateSave(true);
	
				if (!setSkipped ){
	
					String[] types = diskManager.getStorageTypes();
	
					boolean[]	toLinear 	= new boolean[toChange.length];
					boolean[]	toReorder 	= new boolean[toChange.length];
	
					int	num_linear 	= 0;
					int num_reorder	= 0;
	
					for ( int i=0;i<toChange.length;i++){
	
						if ( toChange[i] ){
	
							int old_type = DiskManagerUtil.convertDMStorageTypeFromString( types[i] );
	
							if ( old_type == DiskManagerFileInfo.ST_COMPACT ){
	
								toLinear[i] = true;
	
								num_linear++;
	
							}else if ( old_type == DiskManagerFileInfo.ST_REORDER_COMPACT ){
	
								toReorder[i] = true;
	
								num_reorder++;
							}
						}
					}
	
					if ( num_linear > 0 ){
	
						if (!Arrays.equals(toLinear, setStorageTypes(toLinear, DiskManagerFileInfo.ST_LINEAR))){
	
							return;
						}
					}
	
					if ( num_reorder > 0 ){
	
						if (!Arrays.equals(toReorder, setStorageTypes(toReorder, DiskManagerFileInfo.ST_REORDER ))){
	
							return;
						}
					}
				}
				
				for (int i = 0; i < files.length; i++){
					if (toChange[i]){
					
						files[i].setSkippedInternal( setSkipped );
						
						diskManager.skippedFileSetChanged( files[i] );
					}
				}
			}finally{
				
				dmState.suppressStateSave(false);
			}
				
			DiskManagerUtil.doFileExistenceChecksAfterSkipChange(this, toChange, setSkipped,  diskManager.getDownloadState().getDownloadManager());
		}
	}

	@Override
	public boolean[] setStorageTypes(boolean[] toChange, int newStroageType, boolean force ) {
		if(toChange.length != files.length)
			throw new IllegalArgumentException("array length mismatches the number of files");
		if(files.length == 0)
			return new boolean[0];

		String[] types = diskManager.getStorageTypes();

		boolean[] modified = new boolean[files.length];
		DownloadManagerState	dm_state = diskManager.getDownloadState();

		if (newStroageType == DiskManagerFileInfo.ST_COMPACT || newStroageType == DiskManagerFileInfo.ST_REORDER_COMPACT)
		{
			Debug.out("Download must be stopped for linear -> compact conversion");
			return modified;
		}

		try	{
			dm_state.suppressStateSave(true);

			for (int i = 0; i < files.length; i++)
			{
				if(!toChange[i])
					continue;

				int old_type = DiskManagerUtil.convertDMStorageTypeFromString( types[i] );
				if (newStroageType == old_type)
				{
					modified[i] = true;
					continue;
				}

				DiskManagerFileInfoImpl file = files[i];

				try	{
					file.getCacheFile().setStorageType( DiskManagerUtil.convertDMStorageTypeToCache( newStroageType ), force );
					modified[i] = true;
				} catch (Throwable e) {
					Debug.printStackTrace(e);
					diskManager.setFailedAndRecheck(file, "Failed to change storage type for '" + file.getFile(true) + "': " + Debug.getNestedExceptionMessage(e));
					break;
				} finally {
					types[i] = DiskManagerUtil.convertCacheStorageTypeToString( file.getCacheFile().getStorageType());
				}
			}

			dm_state.setListAttribute(DownloadManagerState.AT_FILE_STORE_TYPES, types);

			for (int i = 0; i < files.length; i++){
				if (toChange[i]){
									
					diskManager.storageTypeChanged( files[i] );
				}
			}
		} finally {
			dm_state.suppressStateSave(false);
			dm_state.save(false);
		}

		return modified;
	}
}
