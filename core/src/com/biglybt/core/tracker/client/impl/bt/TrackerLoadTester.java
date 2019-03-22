/*
 * Created on 1 f�vr. 2005
 * Created by Olivier Chalouhi
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
package com.biglybt.core.tracker.client.impl.bt;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import com.biglybt.core.util.Constants;

/**
 * @author Olivier Chalouhi
 *
 */
public class TrackerLoadTester {

  private static final String trackerUrl = "http://localhost:6969/announce";

  public TrackerLoadTester(int nbTorrents,int nbClientsPerTorrent) {
    for(int i = 0 ; i < nbTorrents ; i++) {
      byte[] hash = generate20BytesHash(i);
      //System.out.println("Adding torrent " + hash);
      for(int j = 0 ; j < nbClientsPerTorrent ; j++) {
        byte[] peerId = generate20BytesHash(j);
        announce(trackerUrl,hash,peerId,6881+j);
      }
    }
  }

  public static void main(String args[]) {
    if(args.length < 2) return;
    int nbTorrents = Integer.parseInt(args[0]);
    int nbClientsPerTorrent = Integer.parseInt(args[1]);
    new TrackerLoadTester(nbTorrents,nbClientsPerTorrent);
  }

  private void announce(String trackerURL,byte[] hash,byte[] peerId,int port) {
    try {
      String strUrl = trackerURL
        + "?info_hash=" + URLEncoder.encode(new String(hash, Constants.BYTE_ENCODING_CHARSET), Constants.BYTE_ENCODING_CHARSET.name()).replaceAll("\\+", "%20")
        + "&peer_id="   + URLEncoder.encode(new String(peerId, Constants.BYTE_ENCODING_CHARSET), Constants.BYTE_ENCODING_CHARSET.name()).replaceAll("\\+", "%20")
      	+ "&port=" + port
      	+ "&uploaded=0&downloaded=0&left=0&numwant=50&no_peer_id=1&compact=1";
      //System.out.println(strUrl);
      URL url = new URL(strUrl);
      URLConnection con = url.openConnection();
      con.connect();
      con.getContent();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  private byte[] generate20BytesHash(int iter) {
    byte[] result = new byte[20];
    int pos = 0;
    while(iter > 0) {
      result[pos++] = (byte) (iter % 255);
      iter = iter / 255;
    }
    return result;
  }

}
