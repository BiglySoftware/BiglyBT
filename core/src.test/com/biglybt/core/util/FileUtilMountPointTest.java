package com.biglybt.core.util;

import java.nio.file.FileStore;
import java.nio.file.FileSystems;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

public class FileUtilMountPointTest
{

	@Test
	public void mountPointWithoutSpaces() {
		assertThat(FileUtil.getMountPoint("/ (/dev/sda1)")).isEqualTo("/");
		assertThat(FileUtil.getMountPoint("/home (/dev/nvme0n1p6)")).isEqualTo("/home");
		assertThat(FileUtil.getMountPoint("/run/user/1000 (tmpfs)")).isEqualTo("/run/user/1000");
	}

	@Test
	public void mountPointWithSpacesIsKeptWhole() {
		assertThat(FileUtil.getMountPoint("/media/user/My Drive (/dev/sdb1)"))
				.isEqualTo("/media/user/My Drive");
		assertThat(FileUtil.getMountPoint("/media/user/My  External Disk (/dev/sdc1)"))
				.isEqualTo("/media/user/My  External Disk");
	}

	@Test
	public void mountPointWithParenthesesIsKeptWhole() {
		assertThat(FileUtil.getMountPoint("/mnt/backup (old) (/dev/sdd1)"))
				.isEqualTo("/mnt/backup (old)");
	}

	@Test
	public void nameWithoutDeviceHasNoMountPoint() {
		assertThat(FileUtil.getMountPoint("/mnt/backup")).isNull();
		assertThat(FileUtil.getMountPoint("")).isNull();
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	public void fileStoreNamesOfThisSystemGiveBackTheirMountPoint() {
		for (FileStore store : FileSystems.getDefault().getFileStores()) {
			String name = String.valueOf(store);
			String device = " (" + store.name() + ")";

			assertThat(name).endsWith(device);
			assertThat(FileUtil.getMountPoint(name) + device).isEqualTo(name);
		}
	}
}
