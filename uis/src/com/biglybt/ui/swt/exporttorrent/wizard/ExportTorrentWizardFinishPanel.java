/*
 * File    : ExportTorrentFinishPanel.java
 * Created : 13-Oct-2003
 * By      : parg
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

package com.biglybt.ui.swt.exporttorrent.wizard;

/**
 * @author parg
 *
 */
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import com.biglybt.core.internat.MessageText;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;

public class
ExportTorrentWizardFinishPanel
	extends AbstractWizardPanel
{

  public
  ExportTorrentWizardFinishPanel(
  	ExportTorrentWizard wizard,
  	IWizardPanel previous)
  {
	super(wizard, previous);
  }

  @Override
  public void show() {
	wizard.setTitle(MessageText.getString("exportTorrentWizard.finish.title"));
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
	Messages.setLanguageText(label, "exportTorrentWizard.finish.message");
  }

  @Override
  public boolean isPreviousEnabled() {
	return( false );
  }

  @Override
  public void
  finish()
  {
	wizard.switchToClose();
  }
}