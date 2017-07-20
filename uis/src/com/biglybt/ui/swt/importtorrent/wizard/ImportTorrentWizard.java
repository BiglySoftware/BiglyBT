/*
 * File    : ImportTorrentWizard.java
 * Created : 13-Oct-2003
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
import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.ui.swt.wizard.Wizard;

public class
ImportTorrentWizard
	extends Wizard
{
	String torrent_file = "";
	String import_file	= "";

	public
	ImportTorrentWizard()
	{
		super("importTorrentWizard.title");

		ImportTorrentWizardInputPanel input_panel = new ImportTorrentWizardInputPanel(this,null);

		this.setFirstPanel(input_panel);
	}

	@Override
	public void
	onClose()
	{
		// Call the parent class to clean up resources
		super.onClose();
	}

	protected void
	setTorrentFile(
		String		str )
	{
		torrent_file = str;
	}

	protected String
	getTorrentFile()
	{
		return( torrent_file );
	}

	protected void
	setImportFile(
		String		str )
	{
		import_file = str;

		torrent_file = str + ".torrent";
	}

	protected String
	getImportFile()
	{
		return( import_file );
	}

	protected boolean
	performImport()
	{
		File input_file;

		try{
			input_file = new File( getImportFile()).getCanonicalFile();

		}catch( IOException e ){

			MessageBox mb = new MessageBox(getWizardWindow(),SWT.ICON_ERROR | SWT.OK );

			mb.setText(MessageText.getString("importTorrentWizard.process.inputfilebad.title"));

			mb.setMessage(	MessageText.getString("importTorrentWizard.process.inputfilebad.message")+"\n" +
							e.toString());

			mb.open();

			return( false );
		}

		File output_file = new File( getTorrentFile() );

		if ( output_file.exists()){

			MessageBox mb = new MessageBox(this.getWizardWindow(),SWT.ICON_QUESTION | SWT.YES | SWT.NO);

			mb.setText(MessageText.getString("importTorrentWizard.process.outputfileexists.title"));

			mb.setMessage(MessageText.getString("importTorrentWizard.process.outputfileexists.message"));

			int result = mb.open();

			if(result == SWT.NO) {

				return( false );
			}
		}

		String	error_title;
		String	error_detail;

		try{

			TOTorrent	torrent;

			try{

				torrent = TOTorrentFactory.deserialiseFromXMLFile( input_file );

				try{

					torrent.serialiseToBEncodedFile( output_file );

					return( true );

				}catch( TOTorrentException e ){

					//e.printStackTrace();

					error_title 	= MessageText.getString("importTorrentWizard.process.torrentfail.title");

					error_detail	= TorrentUtils.exceptionToText( e );
				}
			}catch( TOTorrentException e ){

				// e.printStackTrace();

				error_title 	= MessageText.getString("importTorrentWizard.process.importfail.title");

				error_detail	= TorrentUtils.exceptionToText( e );
			}

		}catch( Throwable e ){

			error_title 	= MessageText.getString("importTorrentWizard.process.unknownfail.title");

			Debug.printStackTrace( e );

			error_detail 	= e.toString();
		}

		MessageBox mb = new MessageBox(this.getWizardWindow(),SWT.ICON_ERROR | SWT.OK );

		mb.setText(error_title);

		mb.setMessage(error_detail);

		mb.open();

		return( false );
	}
}