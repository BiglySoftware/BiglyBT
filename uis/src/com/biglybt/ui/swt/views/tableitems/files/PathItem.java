/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views.tableitems.files;

import java.io.File;
import java.io.IOException;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.FileUtil;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.swt.views.FilesView;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;



public class PathItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{

	private final ParameterListener configShowFullPathListener;
	private boolean show_full_path;

  /** Default Constructor */
  public PathItem() {
    super("path", ALIGN_LEAD, POSITION_LAST, 200, TableManager.TABLE_TORRENT_FILES);

	  configShowFullPathListener = new ParameterListener() {
		  @Override
		  public void parameterChanged(String parameterName) {
			  show_full_path = COConfigurationManager.getBooleanParameter( "FilesView.show.full.path" );
			  invalidateCells();
		  }
	  };
	  COConfigurationManager.addWeakParameterListener(configShowFullPathListener, true,
			  "FilesView.show.full.path");
  }

	@Override
	public void remove() {
		super.remove();

		COConfigurationManager.removeWeakParameterListener(configShowFullPathListener,
				"FilesView.show.full.path");
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

  @Override
  public void refresh(TableCell cell) {
    DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)cell.getDataSource();
    cell.setText(determinePath(fileInfo, show_full_path));
  }

  protected static String determinePath(DiskManagerFileInfo fileInfo, boolean _show_file_path) {

    if( fileInfo == null ) {
    	return "";
    }

    if ( fileInfo instanceof FilesView.FilesViewTreeNode ){
		FilesView.FilesViewTreeNode node = (FilesView.FilesViewTreeNode)fileInfo;
		if ( !node.isLeaf()){
			return( "" );
		}
    }
    
   	boolean has_link = fileInfo.getLink() != null;
   	boolean show_full_path = _show_file_path;

  	DownloadManager dm = fileInfo.getDownloadManager();

  	if ( dm == null ){
  		return( "" );
  	}
   	File dl_save_path_file = dm.getAbsoluteSaveLocation();

   	TOTorrent torrent = dm.getTorrent();

   	if ( torrent != null && torrent.isSimpleTorrent()){

   		dl_save_path_file = dl_save_path_file.getParentFile();
   	}

   	String dl_save_path = dl_save_path_file.getPath();
   	if (!dl_save_path.endsWith(File.separator)) {
   		 dl_save_path += File.separator;
   	}

   	File file = fileInfo.getFile(true);

   	/**
   	 * Figure out whether we should show the full path anyway.
   	 * We'll do this if the path is relative to he current
   	 * download save path.
   	 */
   	//
   	if (has_link && !show_full_path) {
   		show_full_path = !file.getAbsolutePath().startsWith(dl_save_path);
   	}
   	String path = "";

    if (show_full_path) {

    	File parent = file.getParentFile();
    	
    	if ( parent != null ){
		      try {
		          path = FileUtil.getCanonicalPathWithTimeout( parent );
		      }
		      catch( IOException e ) {
		          path = file.getParentFile().getAbsolutePath();
		      }
	
		      if ( !path.endsWith( File.separator )){
	
		    	  path += File.separator;
		      }
    	}
    }else{
    	String apath = file.getAbsolutePath();
    	
    	if ( !apath.startsWith( dl_save_path )){
    		path = apath;
    	}else{
	    	path = apath.substring(dl_save_path.length());
	    	if (path.length() == 0) {
	    		path = File.separator;
	    	}
	    	else {
	    		if (path.charAt(0) == File.separatorChar) {
	    			path = path.substring(1);
	    		}
	    		int	pos = path.lastIndexOf(File.separator);
	
	    		if (pos > 0 ) {
	    			path = File.separator + path.substring( 0, pos );
	    		}
	    		else {
	    			path = File.separator;
	    		}
	    	}
      }
    }

    if ( fileInfo.isSkipped()){

    	String dnd_sf = dm.getDownloadState().getAttribute( DownloadManagerState.AT_DND_SUBFOLDER );

    	if ( dnd_sf != null ){

    		dnd_sf = dnd_sf.trim();

    		if ( dnd_sf.length() > 0 ){

    			if ( show_full_path ){

	    			dnd_sf += File.separatorChar;

	    			if ( path.endsWith( dnd_sf )){

	    				path = path.substring( 0, path.length() - dnd_sf.length());
	    			}
    			}else{

    				if ( path.endsWith( dnd_sf )){

	    				path = path.substring( 0, path.length() - dnd_sf.length());

	    				if ( path.length() > 1 && path.endsWith( File.separator )){

	    					path = path.substring( 0, path.length()-1 );
	    				}
	    			}
    			}
    		}
    	}
    }

    return path;
  }

}
