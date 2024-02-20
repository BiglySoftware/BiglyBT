/*
 * File : Wizard.java Created : 12 oct. 2003 14:30:57 By : Olivier
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */

package com.biglybt.ui.swt.maketorrent;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentCreator;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.ui.swt.FixedURLTransfer;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;
import com.biglybt.ui.swt.wizard.Wizard;

/**
 * @author Olivier
 *
 */
public class
NewTorrentWizard
	extends Wizard
{
	static final int	TT_LOCAL		= 1;
	static final int	TT_EXTERNAL		= 2;
	static final int	TT_DECENTRAL	= 3;

	static final String	TT_EXTERNAL_DEFAULT 	= "http://";
	static final String	TT_DECENTRAL_DEFAULT	= TorrentUtils.getDecentralisedEmptyURL().toString();

	private static String	default_open_dir 	= COConfigurationManager.getStringParameter( "CreateTorrent.default.open", "" );
	private static String	default_save_dir 	= COConfigurationManager.getStringParameter( "CreateTorrent.default.save", "" );
	private static String	comment 			= COConfigurationManager.getStringParameter( "CreateTorrent.default.comment", "" );
	private static String	source 				= COConfigurationManager.getStringParameter( "CreateTorrent.default.source", "" );
	private static int 		tracker_type 		= COConfigurationManager.getIntParameter( "CreateTorrent.default.trackertype", TT_LOCAL );

	static{
			// default the default to the "save torrents to" location

		if ( default_save_dir.length() == 0 ){

			default_save_dir = COConfigurationManager.getStringParameter( "General_sDefaultTorrent_Directory", "" );
		}
	}

  //false : singleMode, true: directory
	 protected static final int MODE_SINGLE_FILE	= 1;
	 protected static final int MODE_DIRECTORY		= 2;
	 protected static final int MODE_BYO			= 3;

  int create_mode = MODE_BYO;
  String singlePath = "";
  String directoryPath = "";
  String savePath = "";

  File	byo_desc_file;
  Map	byo_map;

  private String _trackerURL = TT_EXTERNAL_DEFAULT;

  boolean computed_piece_size = true;
  long	  manual_piece_size;

  boolean 			useMultiTracker = false;
  boolean 			useWebSeed = false;

  private boolean 	addOtherHashes	= 	COConfigurationManager.getBooleanParameter( "CreateTorrent.default.addhashes", false );


  String multiTrackerConfig = "";
  List trackers = new ArrayList();

  String webSeedConfig = "";
  Map	webseeds = new HashMap();

  boolean autoOpen 			= false;
  boolean autoHost 			= false;
  boolean forceStart		= false;
  String  initialTags		= COConfigurationManager.getStringParameter( "CreateTorrent.default.initialTags", "" );
  boolean superseed			= false;
  boolean permitDHT			= true;
  int     torrentVersion	= TOTorrent.TT_V1;
  
  TOTorrentCreator creator = null;

  public
  NewTorrentWizard(
	Display 		display)
  {
    super("wizard.maketorrent.title");

    cancel.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event arg0) {
        if(creator != null) creator.cancel();
      }
    });

    trackers.add(new ArrayList());
    
    String tracker = Utils.getLinkFromClipboard(display);
    
    if ( !isValidTracker( tracker )){
    
    	tracker = getLastTrackerUsed();
    	
    	if ( !isValidTracker( tracker )){
    		
    		tracker = TT_EXTERNAL_DEFAULT;
    	}
    }
    
    setTrackerURL( tracker );
    
    ModePanel panel = new ModePanel(this, null);
    
    createDropTarget(getWizardWindow());
    
    this.setFirstPanel(panel);

  }

  protected int
  getTrackerType()
  {
   	return( tracker_type );
  }

  protected void
  setTrackerType(
	int	type )
  {
	tracker_type = type;

	COConfigurationManager.setParameter( "CreateTorrent.default.trackertype", tracker_type );
  }

  protected String
  getTrackerURL()
  {
	  return( _trackerURL );
  }
  
  protected void
  setTrackerURL(
	String		t )
  {
	  if ( t == null ){
		  
		  t = "";
		  
	  }else{
		  
		  t = t.trim();
	  }
	  
	  _trackerURL = t;
	  
	  if ( isValidTracker( t )){
		  
		  COConfigurationManager.setParameter( "CreateTorrent.tracker.last.used", t );
	  }
  }
  
  protected boolean
  isValidTracker(
	String		tracker )
  {
	  try{
		 URL url = new URL( tracker );
		  
		 String host = url.getHost();
	  
		 return( !host.isEmpty());
		 
	  }catch( Throwable e ){
	  }
		  
	  return( false );
  }
  
  protected String
  getLastTrackerUsed()
  {
	  return( COConfigurationManager.getStringParameter( "CreateTorrent.tracker.last.used", "" ));
  }
  
  protected String
  getDefaultOpenDir()
  {
  	return( default_open_dir );
  }

  protected void
  setDefaultOpenDir(
  	String		d )
  {
  	default_open_dir	= d;

  	COConfigurationManager.setParameter( "CreateTorrent.default.open", default_open_dir );
  }

  protected String
  getDefaultSaveDir()
  {
  	return( default_save_dir );
  }

  protected void
  setDefaultSaveDir(
  	String		d )
  {
  	default_save_dir	= d;

 	COConfigurationManager.setParameter( "CreateTorrent.default.save", default_save_dir );
  }

  protected String
  getInitialTags(
		 boolean	save )
  {
	  if ( save ){
		  COConfigurationManager.setParameter( "CreateTorrent.default.initialTags", initialTags );
	  }
	  return( initialTags );
  }

  protected void
  setInitialTags(
	String		tags )
  {
	  initialTags = tags;
  }

  void setComment(String s) {
    comment = s;

    COConfigurationManager.setParameter("CreateTorrent.default.comment",comment);
  }

  String getComment() {
    return (comment);
  }
  
  void setSource(String s) {
	  source = s.trim();

	  COConfigurationManager.setParameter("CreateTorrent.default.source",source);
  }

  String getSource() {
	  return (source);
  }
  
  private void createDropTarget(final Control control) {
    DropTarget dropTarget = new DropTarget(control, DND.DROP_DEFAULT | DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK);
    dropTarget.setTransfer(new Transfer[] { FixedURLTransfer.getInstance(), FileTransfer.getInstance()});
    dropTarget.addDropListener(new DropTargetAdapter() {
      @Override
      public void dragOver(DropTargetEvent event) {
        if(FixedURLTransfer.getInstance().isSupportedType(event.currentDataType)) {
          event.detail = getCurrentPanel() instanceof ModePanel ? DND.DROP_LINK : DND.DROP_NONE;
        }
      }
      @Override
      public void drop(DropTargetEvent event) {
        if (event.data instanceof String[]) {
          String[] sourceNames = (String[]) event.data;
          if (sourceNames == null )
            event.detail = DND.DROP_NONE;
          if (event.detail == DND.DROP_NONE)
            return;

          for ( String droppedFileStr: sourceNames ){
        	  File droppedFile = new File( droppedFileStr );
	          if (getCurrentPanel() instanceof ModePanel) {
	          } else if (getCurrentPanel() instanceof DirectoryPanel) {
	            if (droppedFile.isDirectory())
	              ((DirectoryPanel) getCurrentPanel()).setFilename(droppedFile.getAbsolutePath());
	          } else if (getCurrentPanel() instanceof SingleFilePanel) {
	            if (droppedFile.isFile())
	              ((SingleFilePanel) getCurrentPanel()).setFilename(droppedFile.getAbsolutePath());
	          } else if (getCurrentPanel() instanceof BYOPanel) {
	        	  ((BYOPanel) getCurrentPanel()).addFilename(droppedFile);

	        	  continue;
	          }
	          break;
          }
        } else if (getCurrentPanel() instanceof ModePanel) {
        	setTrackerURL(((FixedURLTransfer.URLType)event.data).linkURL);
        	((ModePanel) getCurrentPanel()).updateTrackerURL();
        }
       }
    });
  }

  protected void
  setPieceSizeComputed()
  {
  	computed_piece_size = true;
  }

  public boolean
  getPieceSizeComputed()
  {
  	return( computed_piece_size );
  }

  protected void
  setPieceSizeManual(
  	long	_value )
  {
  	computed_piece_size	= false;
  	manual_piece_size	= _value;
  }

  protected long
  getPieceSizeManual()
  {
  	return( manual_piece_size );
  }

  protected void
  setAddOtherHashes(
	boolean	o )
  {
	  addOtherHashes = o;

	  COConfigurationManager.setParameter( "CreateTorrent.default.addhashes", addOtherHashes );

  }

  protected boolean
  getPrivateTorrent()
  {
	  return( COConfigurationManager.getBooleanParameter( "CreateTorrent.default.privatetorrent", false ));
  }

  protected void
  setPrivateTorrent(
	boolean	privateTorrent )
  {
	  COConfigurationManager.setParameter( "CreateTorrent.default.privatetorrent", privateTorrent );
  }

  protected boolean
  getAddOtherHashes()
  {
  	return( addOtherHashes );
  }

  protected IWizardPanel<NewTorrentWizard>
  getNextPanelForMode(
	  AbstractWizardPanel<NewTorrentWizard>		prev )
  {
	  return new BYOPanel( this, prev );
	  /*
	  switch( create_mode ){
	  	case MODE_DIRECTORY:
		  return new DirectoryPanel( this, prev);
	  	case MODE_SINGLE_FILE:
		  return new SingleFilePanel( this, prev );
		default:
		  return new BYOPanel( this, prev );
	  }
	  */
  }
}