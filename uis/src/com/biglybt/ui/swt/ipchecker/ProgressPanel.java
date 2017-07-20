/*
 * File    : ProgressPanel.java
 * Created : 7 oct. 2003 13:01:42
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

package com.biglybt.ui.swt.ipchecker;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.ipchecker.extipchecker.ExternalIPCheckerService;
import com.biglybt.core.ipchecker.extipchecker.ExternalIPCheckerServiceListener;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.wizard.*;

/**
 * @author Olivier
 *
 */
public class ProgressPanel extends AbstractWizardPanel implements ExternalIPCheckerServiceListener {

  Text tasks;
  Display display;

  public ProgressPanel(IpCheckerWizard wizard, IWizardPanel previousPanel) {
    super(wizard, previousPanel);
  }
  /* (non-Javadoc)
   * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#show()
   */
  @Override
  public void show() {
    display = wizard.getDisplay();
    wizard.setTitle(MessageText.getString("ipCheckerWizard.progresstitle"));
    wizard.setCurrentInfo("");
    Composite rootPanel = wizard.getPanel();
    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    rootPanel.setLayout(layout);

    Composite panel = new Composite(rootPanel, SWT.NULL);
    GridData gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
    Utils.setLayoutData(panel, gridData);
    layout = new GridLayout();
    layout.numColumns = 1;
    panel.setLayout(layout);

    tasks = new Text(panel, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY);
    tasks.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
    gridData = new GridData(GridData.FILL_BOTH);
    gridData.heightHint = 120;
    Utils.setLayoutData(tasks, gridData);
  }

  @Override
  public void finish() {
    ((IpCheckerWizard)wizard).selectedService.addListener(this);
    ((IpCheckerWizard)wizard).selectedService.initiateCheck(10000);
  }



  @Override
  public void checkComplete(ExternalIPCheckerService service, String ip) {
    reportProgress(service,MessageText.getString("ipCheckerWizard.checkComplete") + ip);
    IpSetterCallBack callBack = ((IpCheckerWizard)wizard).callBack;
    if(callBack != null) {
      callBack.setIp(ip);
    }
    wizard.switchToClose();
  }


  @Override
  public void checkFailed(ExternalIPCheckerService service, String reason) {
    reportProgress(service,MessageText.getString("ipCheckerWizard.checkFailed") + reason);
    wizard.switchToClose();
  }


  @Override
  public void reportProgress(
    final ExternalIPCheckerService service,
    final String message) {
    if(display == null || display.isDisposed())
      return;
    display.asyncExec(new AERunnable() {
      @Override
      public void runSupport() {
        if (tasks != null && !tasks.isDisposed()) {
          tasks.append(service.getName() + " : " + message + Text.DELIMITER);
        }
      }
    });
  }

}
