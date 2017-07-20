/*
 * File    : TrackerStatusItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
 *
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

package com.biglybt.ui.swt.views.tableitems.mytorrents;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.StringInterner;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableCellToolTipListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 * @author Olivier
 *
 */
public class TrackerNameItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener, TableCellToolTipListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "trackername";
	private static Set<String> preferred_tracker_names;
	private MyParameterListener myParameterListener;

	public TrackerNameItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 120, sTableID);
    setRefreshInterval(5);
  }

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_TRACKER,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);

		myParameterListener = new MyParameterListener();
		COConfigurationManager.addWeakParameterListener(myParameterListener, true,
				"mtv.trackername.pref.hosts");
	}

	@Override
	public void remove() {
		super.remove();

		COConfigurationManager.removeWeakParameterListener(myParameterListener,
				"mtv.trackername.pref.hosts");
	}

	@Override
  public void refresh(TableCell cell) {
    DownloadManager dm = (DownloadManager)cell.getDataSource();
    String name = "";

    if( dm != null && dm.getTorrent() != null ) {
    	TOTorrent torrent = dm.getTorrent();

    	name = getTrackerName(torrent);

    }

    if (cell.setText(name) || !cell.isValid()) {
    	TrackerCellUtils.updateColor(cell, dm, false);
    }
  }

	public static String getTrackerName(TOTorrent torrent) {
		String name =  "";

		Set<String> pref_names = preferred_tracker_names;

  	URL	url = null;

  	if ( pref_names != null ){

  		TOTorrentAnnounceURLSet[] sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();

  		if ( sets.length > 0 ){

  			String host = torrent.getAnnounceURL().getHost();

  			if ( pref_names.contains( host )){

  				url = torrent.getAnnounceURL();

  			}else{

  				for ( TOTorrentAnnounceURLSet set: sets ){

  					URL[] urls = set.getAnnounceURLs();

  					for ( URL u: urls ){

  						if ( pref_names.contains( u.getHost())){

  							url = u;

  							break;
  						}
  					}

  					if ( url != null ){

  						break;
  					}
  				}
  			}
  		}
  	}

  	if ( url == null ){

  		url = torrent.getAnnounceURL();
  	}

  	String host = url.getHost();

  	if ( host.endsWith( ".dht" )){

  		name = "dht";

  	}else{

  		if ( AENetworkClassifier.categoriseAddress( host ) == AENetworkClassifier.AT_PUBLIC ){
	    	String[] parts = host.split( "\\." );

	    	int used = 0;
	    	for( int i = parts.length-1; i >= 0; i-- ) {
	    		if( used > 4 ) break; //don't use more than 4 segments
	    		String chunk = parts[ i ];
	    		if( used < 2 || chunk.length() < 11 ) {  //use first end two always, but trim out >10 chars (passkeys)
	    			if( used == 0 ) name = chunk;
	    			else name = chunk + "." + name;
	    			used++;
	    		}
	    		else break;
	    	}
  		}else{
  			name = host;
  		}
  	}

  	if(name.equals(host)){

  		name = host;

  	}else{

  		name = StringInterner.intern(name);
  	}

  	return name;
	}

	@Override
	public void cellHover(TableCell cell) {
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		cell.setToolTip(TrackerCellUtils.getTooltipText(cell, dm, false));
	}

	@Override
	public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}

	private class MyParameterListener implements ParameterListener {
		@Override
		public void
		parameterChanged(
				String name )
		{
			String prefs = COConfigurationManager.getStringParameter( "mtv.trackername.pref.hosts", null );

			Set<String> new_vals = new HashSet<>();

			if ( prefs != null ){

				String[] bits = prefs.split( ";" );

				for ( String s: bits ){

					s = s.trim();

					if ( s.length() > 0 ){

						new_vals.add( s );
					}
				}
			}

			preferred_tracker_names = new_vals;
		}
	}
}
