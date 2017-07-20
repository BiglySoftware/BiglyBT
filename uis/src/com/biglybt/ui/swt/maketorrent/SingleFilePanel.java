/*
 * File : SingleFilePanel.java Created : 30 sept. 2003 02:50:19 By : Olivier
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;

/**
 * @author Olivier
 *
 */
public class SingleFilePanel extends AbstractWizardPanel<NewTorrentWizard> {
  private Text file;

  public SingleFilePanel(NewTorrentWizard wizard, AbstractWizardPanel<NewTorrentWizard> previous) {
    super(wizard, previous);
  }

  /*
	 * (non-Javadoc)
	 *
	 * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#show()
	 */
  @Override
  public void show() {
    wizard.setTitle(MessageText.getString("wizard.maketorrent.singlefile"));
    wizard.setCurrentInfo(MessageText.getString("wizard.maketorrent.choosefile"));
    Composite panel = wizard.getPanel();
    GridLayout layout = new GridLayout();
    layout.numColumns = 3;
    panel.setLayout(layout);

    Label label = new Label(panel, SWT.NULL);
    Messages.setLanguageText(label, "wizard.maketorrent.file");

    file = new Text(panel, SWT.BORDER);
    file.addModifyListener(new ModifyListener() {
      /*
			 * (non-Javadoc)
			 *
			 * @see org.eclipse.swt.events.ModifyListener#modifyText(org.eclipse.swt.events.ModifyEvent)
			 */
      @Override
      public void modifyText(ModifyEvent arg0) {
        String fName = file.getText();
        wizard.singlePath = fName;
        String error = "";
        if (!fName.equals("")) {
          File f = new File(file.getText());
          if (!f.exists() || f.isDirectory()) {
            error = MessageText.getString("wizard.maketorrent.invalidfile");
          }else{
            String	parent = f.getParent();

            if ( parent != null ){

            	wizard.setDefaultOpenDir( parent );
            }
          }
        }
        wizard.setErrorMessage(error);
        wizard.setNextEnabled(!wizard.singlePath.equals("") && error.equals(""));
      }
    });
    file.setText(((NewTorrentWizard) wizard).singlePath);
    GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
    file.setLayoutData(gridData);

    Button browse = new Button(panel, SWT.PUSH);
    browse.addListener(SWT.Selection, new Listener() {
      /*
			 * (non-Javadoc)
			 *
			 * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
			 */
      @Override
      public void handleEvent(Event arg0) {
        FileDialog fd = new FileDialog(wizard.getWizardWindow());
        if (wizard.getErrorMessage().equals("") && !wizard.singlePath.equals("")) {
          fd.setFileName(wizard.singlePath);
        }else{
        	String	def = wizard.getDefaultOpenDir();

        	if ( def.length() > 0 && new File(def).isDirectory() ){

        		fd.setFilterPath( def );
        	}
        }
        String f = fd.open();
        if (f != null){
            file.setText(f);

            File	ff = new File(f);

            String	parent = ff.getParent();

            if ( parent != null ){

            	wizard.setDefaultOpenDir( parent );
            }
          }

      }
    });
    Messages.setLanguageText(browse, "Button.browse");

    label = new Label(panel, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    label.setLayoutData(gridData);
    label.setText("\n");

    label = new Label(panel, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    label.setLayoutData(gridData);
    label.setForeground(Colors.blue);
    Messages.setLanguageText(label, "wizard.hint.file");
  }

  /*
	 * (non-Javadoc)
	 *
	 * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#getNextPanel()
	 */
  @Override
  public IWizardPanel<NewTorrentWizard> getNextPanel() {
    // TODO Auto-generated method stub
    return new SavePathPanel(((NewTorrentWizard) wizard), this);
  }

  public void setFilename(String filename) {
    file.setText(filename);
  }
 }
