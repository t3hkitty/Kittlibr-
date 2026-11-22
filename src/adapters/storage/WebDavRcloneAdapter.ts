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
