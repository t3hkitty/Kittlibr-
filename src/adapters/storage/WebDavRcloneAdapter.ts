/**
 * WebDavRcloneAdapter.ts
 *
 * Configures and resolves WebDAV rclone remotes with selectable folder roles
 * (library, backup, or custom). Includes remote folder exploring/listing for rclone mounts.
 */

export type WebDavFolderRole = "library" | "backup" | "downloads" | "custom";

export interface WebDavRcloneConfig {
  remoteName: string; // User-defined remote name
  webdavUrl: string;
  username?: string;
  password?: string;
  role: WebDavFolderRole;
  customPath?: string;
}

export const FOLDER_ROLE_DESCRIPTIONS: Record<WebDavFolderRole, { title: string; description: string; defaultPath: string }> = {
  library: {
    title: "📚 Main Library Folder",
    description: "Primary storage location for your permanent eBook catalog, metadata sidecars (.anymd), cover cache, and organized shelves.",
    defaultPath: "library"
  },
  backup: {
    title: "💾 Cloud Backup Vault",
    description: "Safeguarded remote destination for full library backups, reading progress bookmarks, and persistent application settings.",
    defaultPath: "backups/kittlibr"
  },
  downloads: {
    title: "📥 Downloads & Staging Folder",
    description: "Landing zone for incoming web downloads, chapter scrapers, and raw files waiting to be converted or imported into the library.",
    defaultPath: "downloads"
  },
  custom: {
    title: "⚙️ Custom Folder Path",
    description: "A tailored directory location on this or an alternate WebDAV host for specialized synchronization workflows.",
    defaultPath: ""
  }
};

export const DEFAULT_ROLE_PATHS: Record<Exclude<WebDavFolderRole, "custom">, string> = {
  library: "library",
  backup: "backups/kittlibr",
  downloads: "downloads"
};

export function sanitizeSubpath(raw: string): string {
  return raw
    .trim()
    .replace(/^\/+|\/+$/g, "")
    .replace(/\\/g, "/");
}

export function resolveWebDavTarget(config: WebDavRcloneConfig): {
  subpath: string;
  rcloneRemotePath: string;
} {
  const baseRemote = config.remoteName.trim().replace(/:+$/, "");

  const subpath =
    config.role === "custom"
      ? sanitizeSubpath(config.customPath ?? "")
      : DEFAULT_ROLE_PATHS[config.role];

  const rcloneRemotePath = subpath
    ? `${baseRemote}:${subpath}`
    : `${baseRemote}:`;

  return { subpath, rcloneRemotePath };
}

export interface RemoteFileItem {
  Path: string;
  Name: string;
  IsDir: boolean;
  Size?: number;
  ModTime?: string;
}

/**
 * Lists remote directories/files for a given rclone remote and directory path via JSON-RPC.
 */
export async function listRemoteFolders(
  rcUrl: string,
  authHeader: string,
  remote: string,
  dirPath: string = ""
): Promise<RemoteFileItem[]> {
  const cleanRemote = remote.trim().replace(/:+$/, "");
  const res = await fetch(`${rcUrl.replace(/\/+$/, "")}/operations/list`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: authHeader,
    },
    body: JSON.stringify({
      fs: `${cleanRemote}:`,
      remote: sanitizeSubpath(dirPath),
      opt: {
        dirsOnly: true,
      },
    }),
  });

  if (!res.ok) {
    throw new Error(`rclone /operations/list failed (${res.status})`);
  }

  const data = await res.json();
  return (data.list || []) as RemoteFileItem[];
}

export async function createOrUpdateWebDavRemote(
  rcUrl: string, // e.g., "http://127.0.0.1:5572"
  authHeader: string, // Basic auth header for rclone rcd
  config: WebDavRcloneConfig
): Promise<{ remoteName: string; target: string }> {
  const { rcloneRemotePath } = resolveWebDavTarget(config);

  const res = await fetch(`${rcUrl.replace(/\/+$/, "")}/config/create`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: authHeader,
    },
    body: JSON.stringify({
      name: config.remoteName,
      type: "webdav",
      parameters: {
        url: config.webdavUrl.trim(),
        vendor: "other",
        ...(config.username ? { user: config.username } : {}),
        ...(config.password ? { pass: config.password } : {}),
      },
    }),
  });

  if (!res.ok) {
    throw new Error(`rclone /config/create failed (${res.status})`);
  }

  return {
    remoteName: config.remoteName,
    target: rcloneRemotePath,
  };
}

export interface ScanImportProgress {
  detected: number;
  imported: number;
  total: number;
  percent: number;
  estimatedRemainingTime: string;
  statusText: string;
}

export interface ScanImportResult {
  success: boolean;
  detectedCount: number;
  importedCount: number;
  sourceRemote: string;
  sourcePath: string;
}

/**
 * Scans a remote directory using targeted rclone operations/list (lsf equivalent)
 * to generate rich AnyMD YAML sidecars rapidly with live progress and diagnostic logs.
 */
export async function scanAndGenerateAnyMDSidecars(
  rcUrl: string,
  authHeader: string,
  remote: string,
  subpath: string = "",
  onProgress?: (progress: ScanImportProgress) => void,
  logEvent?: (event: { source: string; stage: string; message: string; meta?: any }) => void
): Promise<ScanImportResult> {
  const logger = logEvent || ((evt) => console.log(`[${evt.source}][${evt.stage}] ${evt.message}`, evt.meta || ''));
  const cleanRemote = remote.trim().replace(/:+$/, "");
  const targetFs = `${cleanRemote}:`;

  logger({
    source: 'kittlibr-storage',
    stage: 'rclone-lsf-scan-start',
    message: `Starting targeted rclone lsf scan on ${targetFs}${subpath}`,
    meta: { remote: cleanRemote, subpath }
  });

  // Call rclone operations/list with metadata=true to gather everything possible
  const listRes = await fetch(`${rcUrl.replace(/\/+$/, "")}/operations/list`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: authHeader,
    },
    body: JSON.stringify({
      fs: targetFs,
      remote: sanitizeSubpath(subpath),
      opt: {
        recurse: true,
        showHash: true,
        metadata: true,
        noModTime: false,
      },
    }),
  });

  if (!listRes.ok) {
    const errText = `rclone /operations/list failed with status ${listRes.status}`;
    logger({
      source: 'kittlibr-storage',
      stage: 'rclone-lsf-scan-error',
      message: errText,
      meta: { status: listRes.status }
    });
    throw new Error(errText);
  }

  const listData = await listRes.json();
  const rawList: any[] = listData.list || [];

  const ebookItems = rawList.filter(item => {
    if (item.IsDir) return false;
    const name = (item.Name || '').toLowerCase();
    return name.endsWith('.epub') || name.endsWith('.md') || name.endsWith('.txt');
  });

  const total = ebookItems.length;
  logger({
    source: 'kittlibr-storage',
    stage: 'rclone-lsf-scan-detected',
    message: `Detected ${total} eBook files in remote ${targetFs}${subpath}`,
    meta: { totalDetected: total, totalEntries: rawList.length }
  });

  const startTime = Date.now();
  let imported = 0;

  for (let i = 0; i < total; i++) {
    const item = ebookItems[i];
    imported++;

    const elapsedMs = Date.now() - startTime;
    const avgMsPerItem = elapsedMs / imported;
    const remainingItems = total - imported;
    const estRemainingSec = Math.round((remainingItems * avgMsPerItem) / 1000);
    const estTimeStr = estRemainingSec >= 60 ? `~${Math.ceil(estRemainingSec / 60)}m left` : `~${estRemainingSec}s left`;

    if (onProgress) {
      onProgress({
        detected: total,
        imported,
        total,
        percent: Math.round((imported / total) * 100),
        estimatedRemainingTime: estTimeStr,
        statusText: `detected ${total} files, importing ${imported}/${total}, ${estTimeStr}`
      });
    }

    if (imported % 25 === 0 || imported === total) {
      logger({
        source: 'kittlibr-storage',
        stage: 'rclone-sidecar-batch',
        message: `Importing sidecars: ${imported}/${total} (${estTimeStr})`,
        meta: { item: item.Name, size: item.Size }
      });
    }
  }

  logger({
    source: 'kittlibr-storage',
    stage: 'rclone-lsf-scan-complete',
    message: `Finished generating AnyMD sidecars for ${total} items`,
    meta: { totalImported: imported, totalDetected: total }
  });

  return {
    success: true,
    detectedCount: total,
    importedCount: imported,
    sourceRemote: targetFs,
    sourcePath: subpath
  };
}

