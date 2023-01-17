/*
 * File    : SavePathPanel.java
 * Created : 30 sept. 2003 17:06:45
 * By      : Olivier
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

package com.biglybt.ui.swt.maketorrent;

import java.io.File;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;

/**
 * @author Olivier
 *
 */
public class SavePathPanel extends AbstractWizardPanel<NewTorrentWizard> {

	protected long	file_size;
	protected long	piece_size;
	protected long	piece_count;

  public SavePathPanel(NewTorrentWizard wizard,AbstractWizardPanel<NewTorrentWizard> _previousPanel) {
    super(wizard,_previousPanel);
  }

  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#show()
   */
  @Override
  public void show() {

  	try{
  		if (wizard.create_mode == NewTorrentWizard.MODE_BYO ){
  			file_size = TOTorrentFactory.getTorrentDataSizeFromFileOrDir( wizard.byo_desc_file, true );
  		}else{
  			file_size = TOTorrentFactory.getTorrentDataSizeFromFileOrDir( new File( wizard.create_mode==NewTorrentWizard.MODE_DIRECTORY? wizard.directoryPath: wizard.singlePath), false );
  		}
  		piece_size = TOTorrentFactory.getComputedPieceSize( file_size );

  		piece_count = TOTorrentFactory.getPieceCount( file_size, piece_size );
  	}catch( Throwable e ){
  		Debug.printStackTrace( e );
  	}
    wizard.setTitle(MessageText.getString("wizard.maketorrent.torrentFile"));
    wizard.setCurrentInfo(MessageText.getString("wizard.maketorrent.choosetorrent"));
    Composite panel = wizard.getPanel();
    GridLayout layout = new GridLayout();
    layout.numColumns = 3;
    panel.setLayout(layout);
    Label label;/* = new Label(panel,SWT.NULL);
    Messages.setLanguageText(label,"wizard.maketorrent.file");*/
    final Text file = new Text(panel,SWT.BORDER);

    file.addModifyListener(new ModifyListener() {
      /* (non-Javadoc)
       * @see org.eclipse.swt.events.ModifyListener#modifyText(org.eclipse.swt.events.ModifyEvent)
       */
      @Override
      public void modifyText(ModifyEvent arg0) {
        String fName = file.getText();
        wizard.savePath = fName;
        String error = "";
        if(! fName.equals("")) {
          File f = new File(file.getText());
          if( f.isDirectory() || ( f.getParentFile() != null && !f.getParentFile().canWrite())){
            error = MessageText.getString("wizard.maketorrent.invalidfile");
          }else{
            String	parent = f.getParent();

            if ( parent != null ){

            	wizard.setDefaultSaveDir( parent );
            }
          }
        }
        wizard.setErrorMessage(error);
        wizard.setFinishEnabled(!wizard.savePath.equals("") && error.equals(""));
      }
    });

    String	default_save = wizard.getDefaultSaveDir();

    	// if we have a default save dir then use this as the basis for save location

    String	target_file;

    if( wizard.create_mode == NewTorrentWizard.MODE_BYO ){
    	target_file = "";

    	if (wizard.byo_map != null) {
    		java.util.List list = (java.util.List) wizard.byo_map.get("file_map");
    		if (list != null) {
    			Map map = (Map) list.get(0);
    			if (map != null) {
    				java.util.List path = (java.util.List) map.get("logical_path");
    				if (path != null) {
							target_file = new File(
									COConfigurationManager.getStringParameter("General_sDefaultTorrent_Directory"),
									(String) path.get(0) + ".torrent").getAbsolutePath();
    				}
    			}
    		}
    	}

    }else if ( wizard.create_mode == NewTorrentWizard.MODE_DIRECTORY ){

    	target_file = wizard.directoryPath + ".torrent";

    }else{

    	target_file = wizard.singlePath + ".torrent";
    }


    if ( default_save.length() > 0 && target_file.length() > 0 ){

    	File temp = new File( target_file );

    	String	existing_parent = temp.getParent();

    	if ( existing_parent != null ){

    		target_file	= new File( default_save, temp.getName()).toString();
    	}
    }

    wizard.savePath = target_file;

    file.setText( wizard.savePath);
    GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 2;
    file.setLayoutData(gridData);
    Button browse = new Button(panel,SWT.PUSH);
    browse.addListener(SWT.Selection,new Listener() {
      /* (non-Javadoc)
       * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
       */
      @Override
      public void handleEvent(Event arg0) {
        FileDialog fd = new FileDialog(wizard.getWizardWindow(),SWT.SAVE);
        final String path = wizard.savePath;
        if(wizard.getErrorMessage().equals("") && !path.equals("")) {
            File fsPath = new File(path);
            if(!path.endsWith(File.separator)) {
                fd.setFilterPath(fsPath.getParent());
                fd.setFileName(fsPath.getName());
            }
            else {
                fd.setFileName(path);
            }
        }
        String f = fd.open();
        if (f != null){
            file.setText(f);

            File	ff = new File(f);

            String	parent = ff.getParent();

            if ( parent != null ){
                wizard.setDefaultSaveDir( parent );
            }
          }
      }
    });
    Messages.setLanguageText(browse, "Button.browse");

    	// ----------------------

    Control sep = Utils.createSkinnedLabelSeparator(panel, SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    sep.setLayoutData(gridData);

    Composite gFileStuff = new Composite(panel, SWT.NULL);
    gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
    gridData.horizontalSpan = 3;
    gFileStuff.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 4;
    gFileStuff.setLayout(layout);

    	// file size

    label = new Label(gFileStuff, SWT.NULL);
    Messages.setLanguageText(label, "wizard.maketorrent.filesize");

    Label file_size_label = new Label(gFileStuff, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan=3;
    file_size_label.setLayoutData(gridData);
    file_size_label.setText( DisplayFormatters.formatByteCountToKiBEtc(file_size));

    	// piece count

    label = new Label(gFileStuff, SWT.NULL);
    Messages.setLanguageText(label, "wizard.maketorrent.piececount");

    final Label piece_count_label = new Label(gFileStuff, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan=3;
    piece_count_label.setLayoutData(gridData);
    piece_count_label.setText( ""+piece_count );
 
   		// piece size

    label = new Label(gFileStuff, SWT.NULL);
    Messages.setLanguageText(label, "wizard.maketorrent.piecesize");

    final Label piece_size_label = new Label(gFileStuff, SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 75;
    piece_size_label.setLayoutData(gridData);
    piece_size_label.setText( DisplayFormatters.formatByteCountToKiBEtc( piece_size ));

    final Combo manual = new Combo(gFileStuff, SWT.SINGLE | SWT.READ_ONLY);

    final long[] sizes = TOTorrentFactory.STANDARD_PIECE_SIZES;

    manual.add( MessageText.getString( "wizard.maketorrent.auto"));

    for (int i=0;i<sizes.length;i++){
    	manual.add(DisplayFormatters.formatByteCountToKiBEtc(sizes[i]));
    }

    manual.select(0);

    manual.addListener(SWT.Selection, new Listener() {
    	@Override
	    public void
    	handleEvent(
    			Event e)
    	{
    		int	index = manual.getSelectionIndex();

    		if ( index == 0 ){

    			wizard.setPieceSizeComputed();

    			piece_size = TOTorrentFactory.getComputedPieceSize( file_size );

     		}else{
    			piece_size = sizes[index-1];

    			wizard.setPieceSizeManual(piece_size);
    		}

    		piece_count = TOTorrentFactory.getPieceCount( file_size, piece_size );

    		piece_size_label.setText( DisplayFormatters.formatByteCountToKiBEtc(piece_size));
    		piece_count_label.setText( ""+piece_count );
    	}
    });

    label = new Label(gFileStuff, SWT.NULL);

    // ------------------------
    sep = Utils.createSkinnedLabelSeparator(panel, SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    sep.setLayoutData(gridData);

    final Button bAutoOpen = new Button(panel,SWT.CHECK);
    Messages.setLanguageText(bAutoOpen,"wizard.maketorrents.autoopen");
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    bAutoOpen.setLayoutData(gridData);

    final Button bforce = new Button(panel,SWT.CHECK);
    Messages.setLanguageText(bforce,"wizard.maketorrents.force");
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    bforce.setLayoutData(gridData);

    final Button bSuperSeed = new Button(panel,SWT.CHECK);
    Messages.setLanguageText(bSuperSeed,"wizard.maketorrents.superseed");
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    bSuperSeed.setLayoutData(gridData);

    final Button bAutoHost = new Button(panel,SWT.CHECK);
    Messages.setLanguageText(bAutoHost,"wizard.maketorrents.autohost");
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    bAutoHost.setLayoutData(gridData);

    label = new Label(panel,SWT.NULL);
    Messages.setLanguageText(label,"wizard.maketorrents.init.tags");
    final Text tag_area = new Text(panel,SWT.BORDER);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 2;
    tag_area.setLayoutData(gridData);

    bforce.setEnabled( false );
    tag_area.setEnabled( false );
    bSuperSeed.setEnabled( false );
    bAutoHost.setEnabled( false );

    bAutoOpen.addListener(SWT.Selection,new Listener(){
        @Override
        public void handleEvent(Event event){
          boolean autoOpen = wizard.autoOpen = bAutoOpen.getSelection();

          boolean enable = autoOpen && wizard.getTrackerType() != NewTorrentWizard.TT_EXTERNAL;

          bforce.setEnabled( autoOpen );
          tag_area.setEnabled( autoOpen );
          bSuperSeed.setEnabled( autoOpen );
          bAutoHost.setEnabled( enable );
        }
      });

    bforce.addListener(SWT.Selection,new Listener(){
        @Override
        public void handleEvent(Event event) {
          wizard.forceStart = bforce.getSelection();
        }
      });

    tag_area.setText( wizard.getInitialTags(false));
    tag_area.addModifyListener(new ModifyListener(){
        @Override
        public void modifyText(ModifyEvent arg0){
        	wizard.setInitialTags( tag_area.getText().trim());
        }
    });


    bSuperSeed.addListener(SWT.Selection,new Listener(){
        @Override
        public void handleEvent(Event event){
          wizard.superseed = bSuperSeed.getSelection();
        }
      });

    bAutoHost.addListener(SWT.Selection,new Listener(){
      @Override
      public void handleEvent(Event event){
        wizard.autoHost = bAutoHost.getSelection();
      }
    });

    final Button bPrivateTorrent = new Button(panel,SWT.CHECK);
    Messages.setLanguageText(bPrivateTorrent,"ConfigView.section.sharing.privatetorrent");
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    bPrivateTorrent.setLayoutData(gridData);


    final Button bAllowDHT = new Button(panel,SWT.CHECK);
    Messages.setLanguageText(bAllowDHT,"ConfigView.section.sharing.permitdht");
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    bAllowDHT.setLayoutData(gridData);
    bAllowDHT.setSelection( true );

    bAllowDHT.addListener(SWT.Selection,new Listener() {
        @Override
        public void handleEvent(Event event) {
          wizard.permitDHT = bAllowDHT.getSelection();
        }
      });

    	// terrible code, who wrote this?

    if ( wizard.getTrackerType() == NewTorrentWizard.TT_DECENTRAL ){

    	wizard.setPrivateTorrent( false );
    }

    boolean privateTorrent = wizard.getPrivateTorrent();

	bAllowDHT.setEnabled( !privateTorrent );
    if ( privateTorrent ){

  	  bAllowDHT.setSelection( false );
  	  wizard.permitDHT = false;
    }

	bPrivateTorrent.addListener(SWT.Selection,new Listener() {
        @Override
        public void handleEvent(Event event) {
          boolean privateTorrent = bPrivateTorrent.getSelection();

          wizard.setPrivateTorrent(privateTorrent);

          if ( privateTorrent ){

        	  bAllowDHT.setSelection( false );
        	  wizard.permitDHT = false;
          }
		  bAllowDHT.setEnabled( !privateTorrent );
        }
      });

    if ( wizard.getTrackerType() == NewTorrentWizard.TT_DECENTRAL ){

		bAllowDHT.setEnabled( false );
		bPrivateTorrent.setEnabled( false );
    }else{
    	bPrivateTorrent.setSelection( privateTorrent );
    }
    
    	// torrent version
    
    label = new Label(panel, SWT.NULL);
    Messages.setLanguageText(label, "label.torrent.version");

   
    final Combo torrent_Version = new Combo(panel, SWT.SINGLE | SWT.READ_ONLY);

    int[] torrent_versions = { TOTorrent.TT_V1, TOTorrent.TT_V1_V2, TOTorrent.TT_V2 };
    
    for ( int i=1;i<=3;i++){
    
    	torrent_Version.add( MessageText.getString( "torrent.version.type." + i));
    }
    
    torrent_Version.select(0);

    wizard.torrentVersion = TOTorrent.TT_V1;
    
    torrent_Version.addListener(SWT.Selection, new Listener() {
    	@Override
	    public void
    	handleEvent(
    			Event e)
    	{
    		int	index = torrent_Version.getSelectionIndex();

    		wizard.torrentVersion = torrent_versions[index];
    	}
    });

    label = new Label(gFileStuff, SWT.NULL);
  }

  @Override
  public IWizardPanel<NewTorrentWizard> getFinishPanel() {
    return new ProgressPanel( wizard, this );
  }

  @Override
  public boolean
  isFinishSelectionOK()
  {
	  String save_path = wizard.savePath;

	  File f = new File( save_path );

	  if ( f.isFile()){
		  MessageBox mb = new MessageBox(wizard.getWizardWindow(),SWT.ICON_QUESTION | SWT.YES | SWT.NO);

		  mb.setText(MessageText.getString("exportTorrentWizard.process.outputfileexists.title"));

		  mb.setMessage(MessageText.getString("exportTorrentWizard.process.outputfileexists.message"));

		  int result = mb.open();

		  if( result == SWT.NO ){

			  return( false );
		  }
	  }

	  return( true );
  }
}
