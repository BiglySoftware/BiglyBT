/*
 * Created on Feb 6, 2007
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */


package com.biglybt.core.networkmanager;

public abstract class
NetworkConnectionHelper
	implements NetworkConnectionBase
{
	  int	upload_limit;

	  private final LimitedRateGroup upload_limiter = new LimitedRateGroup() {
			@Override
			public String
			getName()
			{
				return( "per_con_up: " + getString());
			}
			@Override
			public int getRateLimitBytesPerSecond()
			{
				return upload_limit;
			}
			@Override
			public boolean
			isDisabled()
			{
				return( upload_limit == -1 );
			}
			@Override
			public void
			updateBytesUsed(
					int	used )
			{
			}
	  };

	  int	download_limit;

	  private final LimitedRateGroup download_limiter =
		  new LimitedRateGroup()
	  	{
			@Override
			public String
			getName()
			{
				return( "per_con_down: " + getString());
			}
			@Override
			public int getRateLimitBytesPerSecond() {  return download_limit;  }
			@Override
			public boolean
			isDisabled()
			{
				return( download_limit == -1 );
			}
			@Override
			public void
			updateBytesUsed(
					int	used )
			{
			}
	  };

	  private volatile LimitedRateGroup[]	upload_limiters 	= { upload_limiter };
	  private volatile LimitedRateGroup[]	download_limiters 	= { download_limiter };

		@Override
		public int
		getUploadLimit()
		{
			return( upload_limit );
		}

		@Override
		public int
		getDownloadLimit()
		{
			return( download_limit );
		}

		@Override
		public void
		setUploadLimit(
			int		limit )
		{
			upload_limit = limit;
		}

		@Override
		public void
		setDownloadLimit(
			int		limit )
		{
			download_limit = limit;
		}

		@Override
		public void
		addRateLimiter(
			LimitedRateGroup 	limiter,
			boolean 			upload	)
		{
			synchronized( this ){

				if ( upload ){

					for (int i=0;i<upload_limiters.length;i++){

						if ( upload_limiters[i] == limiter ){

							return;
						}
					}

					LimitedRateGroup[] new_upload_limiters = new LimitedRateGroup[upload_limiters.length+1];

					System.arraycopy(upload_limiters, 0, new_upload_limiters, 0, upload_limiters.length );

					new_upload_limiters[ upload_limiters.length ] = limiter;

					upload_limiters = new_upload_limiters;
				}else{

					for (int i=0;i<download_limiters.length;i++){

						if ( download_limiters[i] == limiter ){

							return;
						}
					}
					LimitedRateGroup[] new_download_limiters = new LimitedRateGroup[download_limiters.length+1];

					System.arraycopy(download_limiters, 0, new_download_limiters, 0, download_limiters.length );

					new_download_limiters[ download_limiters.length ] = limiter;

					download_limiters = new_download_limiters;
				}
			}

			NetworkManager.getSingleton().addRateLimiter( this, limiter, upload );
		}

		@Override
		public void
		removeRateLimiter(
			LimitedRateGroup 	limiter,
			boolean 			upload )
		{
			synchronized( this ){

				if ( upload ){

					if ( upload_limiters.length == 0 ){

						return;
					}

					int	pos = 0;

					LimitedRateGroup[] new_upload_limiters = new LimitedRateGroup[upload_limiters.length-1];

					for (int i=0;i<upload_limiters.length;i++){

						if ( upload_limiters[i] != limiter ){

							if ( pos == new_upload_limiters.length ){

								return;
							}

							new_upload_limiters[pos++] = upload_limiters[i];
						}
					}

					upload_limiters = new_upload_limiters;

				}else{

					if ( download_limiters.length == 0 ){

						return;
					}

					int	pos = 0;

					LimitedRateGroup[] new_download_limiters = new LimitedRateGroup[download_limiters.length-1];

					for (int i=0;i<download_limiters.length;i++){

						if ( download_limiters[i] != limiter ){

							if ( pos == new_download_limiters.length ){

								return;
							}

							new_download_limiters[pos++] = download_limiters[i];
						}
					}

					download_limiters = new_download_limiters;
				}
			}

			NetworkManager.getSingleton().removeRateLimiter( this, limiter, upload );
		}

		@Override
		public LimitedRateGroup[]
		getRateLimiters(
			boolean upload )
		{
			if ( upload ){

				return( upload_limiters );

			}else{

				return( download_limiters );
			}
		}
}
