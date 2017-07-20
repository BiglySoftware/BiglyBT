/*
 * File    : ImportTorrentWizardInputPanel.java
 * Created : 14-Oct-2003
 * By      : stuff
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

package com.biglybt.ui.swt.importtorrent.wizard;

/**
 * @author parg
 *
 */

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Constants;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.wizard.*;

public class
ImportTorrentWizardInputPanel
	extends AbstractWizardPanel
{
	protected boolean file_valid = false;

	  public
	ImportTorrentWizardInputPanel(
		Wizard 					wizard,
		IWizardPanel 			previous )
	  {
		super(wizard, previous);
	  }


	  @Override
	  public void
	  show()
	  {
		wizard.setTitle(MessageText.getString("importTorrentWizard.importfile.title"));

		Composite rootPanel = wizard.getPanel();
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		rootPanel.setLayout(layout);

		Composite panel = new Composite(rootPanel, SWT.NULL);
		GridData gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(panel, gridData);
		layout = new GridLayout();
		layout.numColumns = 3;
		panel.setLayout(layout);

		Label label = new Label(panel, SWT.WRAP);
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		gridData.widthHint = 380;
		Utils.setLayoutData(label, gridData);
		Messages.setLanguageText(label, "importTorrentWizard.importfile.message");

		label = new Label(panel,SWT.NULL);
		Messages.setLanguageText(label, "importTorrentWizard.importfile.path");

		final Text textPath = new Text(panel,SWT.BORDER);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(textPath, gridData);
		textPath.setText("");

		Button browse = new Button(panel,SWT.PUSH);
		Messages.setLanguageText(browse, "importTorrentWizard.importfile.browse");
		browse.addListener(SWT.Selection,new Listener() {
		  @Override
		  public void handleEvent(Event arg0) {

			FileDialog fd = new FileDialog(wizard.getWizardWindow());

			fd.setFileName(textPath.getText());

			fd.setFilterExtensions(new String[]{"*.xml", Constants.FILE_WILDCARD});

			String path = fd.open();

			if(path != null) {

			  textPath.setText(path);
			}
		  }
		});

		textPath.addListener(SWT.Modify, new Listener(){

		  @Override
		  public void handleEvent(Event event) {

			String path = textPath.getText();

			((ImportTorrentWizard)wizard).setImportFile( path );

			file_valid = false;

			try{

			  File f = new File(path);

			  if( f.exists()){

			  	if (f.isFile()){

			  		file_valid = true;

					wizard.setErrorMessage("");
			  	}else{

					wizard.setErrorMessage(MessageText.getString("importTorrentWizard.importfile.invalidPath"));
			  	}
			  }

			}catch(Exception e){
			  wizard.setErrorMessage(MessageText.getString("importTorrentWizard.importfile.invalidPath"));
			}

			wizard.setNextEnabled( file_valid );
		  }
		});

		textPath.setText(((ImportTorrentWizard)wizard).getImportFile());

		textPath.setFocus();
	}

	@Override
	public IWizardPanel
	getNextPanel()
	{
		return( new ImportTorrentWizardOutputPanel(wizard,this));
	}

	@Override
	public boolean
	isNextEnabled()
	{
	   return( file_valid );
	}
}