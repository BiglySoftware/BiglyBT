/*
 * File    : FilePanel.java
 * Created : 13 oct. 2003 01:31:52
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

package com.biglybt.ui.swt.config.wizard;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;

/**
 * @author Olivier
 *
 */
public class FilePanel extends AbstractWizardPanel<ConfigureWizard> {

  public FilePanel(ConfigureWizard wizard, IWizardPanel<ConfigureWizard> previous) {
    super(wizard, previous);
  }


  @Override
  public void show() {

    wizard.setTitle(MessageText.getString("configureWizard.file.title"));
    //wizard.setCurrentInfo(MessageText.getString("configureWizard.nat.hint"));
    Composite rootPanel = wizard.getPanel();
    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    rootPanel.setLayout(layout);

    Composite panel = new Composite(rootPanel, SWT.NULL);
    GridData gridData = new GridData(GridData.FILL_BOTH);
	  panel.setLayoutData(gridData);
    layout = new GridLayout();
    layout.numColumns = 3;
    panel.setLayout(layout);

    {
    	// data

	    Label label = new Label(panel, SWT.WRAP);
	    gridData = new GridData(GridData.FILL_HORIZONTAL);
	    gridData.horizontalSpan = 3;
	    label.setLayoutData(gridData);
	    Messages.setLanguageText(label, "configureWizard.file.message3");

	    label = new Label(panel,SWT.NULL);
	    label.setLayoutData(new GridData());
	    Messages.setLanguageText(label, "configureWizard.file.path");

	    final Text textPath = new Text(panel,SWT.BORDER);
	    gridData = new GridData(GridData.FILL_HORIZONTAL);
	    gridData.widthHint = 100;
	    textPath.setLayoutData(gridData);
	    textPath.setText(((ConfigureWizard)wizard).getDataPath());

	    Button browse = new Button(panel,SWT.PUSH);
	    Messages.setLanguageText(browse, "configureWizard.file.browse");
	    browse.setLayoutData(new GridData());
	    browse.addListener(SWT.Selection,new Listener() {
	      @Override
	      public void handleEvent(Event arg0) {
	        DirectoryDialog dd = new DirectoryDialog(wizard.getWizardWindow());
	        dd.setFilterPath(textPath.getText());
	        String path = dd.open();
	        if(path != null) {
	          textPath.setText(path);
	        }
	      }
	    });

	    textPath.addListener(SWT.Modify, new Listener() {
	      @Override
	      public void handleEvent(Event event) {
	        String path = textPath.getText();
	        ((ConfigureWizard)wizard).setDataPath( path );
	        try {
	          File f = new File(path);
	          if(f.exists() && f.isDirectory()) {
	            wizard.setErrorMessage("");
	            wizard.setFinishEnabled(true);
	          } else {
	            wizard.setErrorMessage(MessageText.getString("configureWizard.file.invalidPath"));
	            wizard.setFinishEnabled(false);
	          }
	        } catch(Exception e) {
	          wizard.setErrorMessage(MessageText.getString("configureWizard.file.invalidPath"));
	          wizard.setFinishEnabled(false);
	        }
	      }
	    });
    }


    {
    	// torrents

	    Label label = new Label(panel, SWT.WRAP);
	    gridData = new GridData(GridData.FILL_HORIZONTAL);
	    gridData.horizontalSpan = 3;
	    label.setLayoutData(gridData);
	    Messages.setLanguageText(label, "configureWizard.file.message1");

	    label = new Label(panel,SWT.NULL);
	    label.setLayoutData(new GridData());
	    Messages.setLanguageText(label, "configureWizard.file.path");

	    final Text textPath = new Text(panel,SWT.BORDER);
	    gridData = new GridData(GridData.FILL_HORIZONTAL);
	    gridData.widthHint = 100;
	    textPath.setLayoutData(gridData);
	    textPath.setText(((ConfigureWizard)wizard).torrentPath);

	    Button browse = new Button(panel,SWT.PUSH);
	    Messages.setLanguageText(browse, "configureWizard.file.browse");
	    browse.setLayoutData(new GridData());
	    browse.addListener(SWT.Selection,new Listener() {
	      @Override
	      public void handleEvent(Event arg0) {
	        DirectoryDialog dd = new DirectoryDialog(wizard.getWizardWindow());
	        dd.setFilterPath(textPath.getText());
	        String path = dd.open();
	        if(path != null) {
	          textPath.setText(path);
	        }
	      }
	    });

	    textPath.addListener(SWT.Modify, new Listener() {
	      @Override
	      public void handleEvent(Event event) {
	        String path = textPath.getText();
	        ((ConfigureWizard)wizard).torrentPath = path;
	        try {
	          File f = new File(path);
	          if(f.exists() && f.isDirectory()) {
	            wizard.setErrorMessage("");
	            wizard.setFinishEnabled(true);
	          } else {
	            wizard.setErrorMessage(MessageText.getString("configureWizard.file.invalidPath"));
	            wizard.setFinishEnabled(false);
	          }
	        } catch(Exception e) {
	          wizard.setErrorMessage(MessageText.getString("configureWizard.file.invalidPath"));
	          wizard.setFinishEnabled(false);
	        }
	      }
	    });

	    textPath.setText(((ConfigureWizard)wizard).torrentPath);
    }
  }

  @Override
  public IWizardPanel<ConfigureWizard> getFinishPanel() {
    return new FinishPanel(((ConfigureWizard)wizard),this);
  }

}
