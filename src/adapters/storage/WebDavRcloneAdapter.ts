/**
 * WebDavRcloneAdapter.ts
 *
 * Configures and resolves WebDAV rclone remotes with selectable folder roles
 * (library, backup, or custom).
 */

export type WebDavFolderRole = "library" | "backup" | "custom";

export interface WebDavRcloneConfig {
  remoteName: string; // e.g., "torbox_webdav" or "meow_webdav"
  webdavUrl: string;
  username?: string;
  password?: string;
  role: WebDavFolderRole;
  customPath?: string;
}

const DEFAULT_ROLE_PATHS: Record<Exclude<WebDavFolderRole, "custom">, string> = {
  library: "library",
  backup: "backups/kittlibr",
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
