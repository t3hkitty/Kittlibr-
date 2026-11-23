/**
 * librsync_plugin.ts — Kittlibr 6-Slot Extension for Headless Vault Sync & Vaultr Button Triggers
 * Slot: 'librsync'
 * Communicates strictly via loopback HTTP/JSON (:3050 and :5572) without WinFsp/FUSE mounts.
 */

export interface VaultrStatusResponse {
  status: 'idle' | 'idle_no_files' | 'running' | 'completed' | 'blocked_space';
  is_locked: boolean;
  incoming_count: number;
  staging_count: number;
  sidecar_count: number;
  destination_count: number;
  staging_space: {
    free_bytes?: number;
    total_bytes?: number;
    sufficient_for_unpack?: boolean;
    min_required_bytes?: number;
  };
  destination_space: {
    free_bytes?: number;
    total_bytes?: number;
  };
  last_run: string | null;
  last_processed_count: number;
  discord_configured: boolean;
  destination_remote: string;
}

export interface VaultrTriggerOptions {
  destinationRemote?: string;
  discordWebhookUrl?: string;
  minFreeSpaceMb?: number;
}

export class LibrsyncClient {
  private daemonUrl: string;

  constructor(daemonUrl = 'http://127.0.0.1:3050') {
    this.daemonUrl = daemonUrl.replace(/\/+$/, '');
  }

  /**
   * Safe, read-only GET query layer for tier status, free space, and queue size.
   * Won't initiate heavy sync operations accidentally.
   */
  async getStatus(): Promise<VaultrStatusResponse> {
    const res = await fetch(`${this.daemonUrl}/api/vaultr/status`);
    if (!res.ok) {
      throw new Error(`Failed to fetch Vaultr status (${res.status}): ${await res.text()}`);
    }
    return res.json();
  }

  /**
   * User button-driven trigger to run the Seedr 4-folder pipeline
   * (Incoming -> Staging -> Sidecar Ready -> Main Vault).
   */
  async triggerSync(options?: VaultrTriggerOptions): Promise<{
    success: boolean;
    processed_count: number;
    processed_items?: Array<{ name: string; destination: string; sidecar: string }>;
    duration_sec?: number;
    errors?: string[];
    message?: string;
  }> {
    const payload: Record<string, any> = {};
    if (options?.destinationRemote) payload.destination_remote = options.destinationRemote;
    if (options?.discordWebhookUrl) payload.discord_webhook_url = options.discordWebhookUrl;
    if (options?.minFreeSpaceMb) payload.min_free_space_mb = options.minFreeSpaceMb;

    const res = await fetch(`${this.daemonUrl}/api/vaultr/trigger`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (res.status === 409) {
      throw new Error('Vaultr sync is already in progress (single-flight lock active).');
    }
    if (!res.ok) {
      throw new Error(`Vaultr trigger failed (${res.status}): ${await res.text()}`);
    }
    return res.json();
  }
}
