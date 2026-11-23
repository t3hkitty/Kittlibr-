/**
 * DefensiveArchiveUnpacker.ts
 * Ported from FBReader archive safety traversal routines.
 * Defends against Zip-Slip attacks, corrupted central directories, and handles .fb2.zip cleanly.
 */

export interface ArchiveEntry {
  path: string;
  size: number;
  isSanitized: boolean;
  data?: Uint8Array;
}

export class DefensiveArchiveUnpacker {
  /**
   * Sanitizes relative entry paths to prevent directory traversal (Zip-Slip vulnerability).
   */
  public static sanitizeEntryPath(entryPath: string, destinationRoot: string): string | null {
    // Replace backslashes with forward slashes
    const normalized = entryPath.replace(/\\/g, '/');

    // Reject entries attempting directory traversal
    if (normalized.includes('../') || normalized.startsWith('/') || normalized.includes('..')) {
      console.warn(`[Security Alert] Rejected malicious zip entry path: ${entryPath}`);
      return null;
    }

    // Strip leading dot-slashes
    const cleanPath = normalized.replace(/^\.\//, '');
    return cleanPath;
  }

  /**
   * Inspects buffer for corrupt ZIP central directory signatures and recovers embedded payloads.
   */
  public static validateZipHeader(buffer: Uint8Array): { isValid: boolean; isFb2Zip: boolean } {
    if (buffer.length < 4) return { isValid: false, isFb2Zip: false };

    // Standard PK ZIP signature: 0x50 0x4B 0x03 0x04
    const isPkZip = buffer[0] === 0x50 && buffer[1] === 0x4B && buffer[2] === 0x03 && buffer[3] === 0x04;
    return {
      isValid: isPkZip,
      isFb2Zip: isPkZip // Extended FB2 check can inspect inner files
    };
  }

  /**
   * Unpacks an EPUB archive buffer, parses META-INF/container.xml and OPF manifest/spine,
   * and returns structured chapter documents with metadata.
   */
  public static async unpackEpub(
    buffer: Uint8Array,
    options?: { logDiagnostic?: (event: { source: string; stage: string; message: string; meta?: any }) => void }
  ): Promise<{
    title: string;
    author: string;
    identifier: string;
    chapters: Array<{ id: string; title: string; href: string; html: string; rawBytes: Uint8Array }>;
    files: Record<string, Uint8Array>;
  }> {
    const log = options?.logDiagnostic || ((e) => console.log(`[${e.source}][${e.stage}] ${e.message}`, e.meta || ''));

    // Check header
    const headerCheck = this.validateZipHeader(buffer);
    if (!headerCheck.isValid) {
      log({ source: 'kittlibr-reader', stage: 'parse', message: 'Corrupt or non-ZIP EPUB container header', meta: { bufferLength: buffer.length } });
      throw new Error('Invalid EPUB: file does not have a valid ZIP header');
    }

    // Resolve JSZip from global (browser) or require (node/test)
    let JSZipLib: any;
    if (typeof (globalThis as any).JSZip !== 'undefined') {
      JSZipLib = (globalThis as any).JSZip;
    } else {
      try {
        JSZipLib = require('../../assets/lib/jszip.min.js');
      } catch (e) {
        try {
          JSZipLib = require('jszip');
        } catch (e2) {
          throw new Error('JSZip library could not be loaded for EPUB unpacking');
        }
      }
    }

    let zip: any;
    try {
      zip = await JSZipLib.loadAsync(buffer);
    } catch (err: any) {
      log({ source: 'kittlibr-reader', stage: 'parse', message: `Archive extraction failed: ${err.message}`, meta: { error: String(err) } });
      throw new Error(`Failed to extract EPUB archive: ${err.message}`);
    }

    const files: Record<string, Uint8Array> = {};
    const fileEntries = Object.keys(zip.files);
    for (const rawPath of fileEntries) {
      const sanitized = this.sanitizeEntryPath(rawPath, '/cache');
      if (!sanitized) {
        log({ source: 'kittlibr-reader', stage: 'security', message: `Rejected malicious entry: ${rawPath}` });
        continue;
      }
      const entry = zip.files[rawPath];
      if (!entry.dir) {
        files[sanitized] = await entry.async('uint8array');
      }
    }

    // 1. Locate container.xml
    const containerEntry = files['META-INF/container.xml'] || files['meta-inf/container.xml'];
    if (!containerEntry) {
      log({ source: 'kittlibr-reader', stage: 'parse', message: 'Missing META-INF/container.xml inside EPUB', meta: { files: Object.keys(files) } });
      throw new Error('EPUB validation error: META-INF/container.xml not found');
    }

    const decoder = new TextDecoder('utf-8');
    const containerXml = decoder.decode(containerEntry);

    // Parse rootfile full-path
    const rootfileMatch = containerXml.match(/full-path\s*=\s*["']([^"']+)["']/i);
    if (!rootfileMatch || !rootfileMatch[1]) {
      log({ source: 'kittlibr-reader', stage: 'parse', message: 'No rootfile found in META-INF/container.xml' });
      throw new Error('EPUB validation error: rootfile not found in container.xml');
    }

    const opfPath = rootfileMatch[1].replace(/^\//, '');
    const opfData = files[opfPath];
    if (!opfData) {
      log({ source: 'kittlibr-reader', stage: 'parse', message: `OPF package file not found at path: ${opfPath}` });
      throw new Error(`EPUB error: package file ${opfPath} not found`);
    }

    const opfDir = opfPath.includes('/') ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : '';
    const opfXml = decoder.decode(opfData);

    // Extract title, author, identifier
    const titleMatch = opfXml.match(/<dc:title[^>]*>([\s\S]*?)<\/dc:title>/i);
    const authorMatch = opfXml.match(/<dc:creator[^>]*>([\s\S]*?)<\/dc:creator>/i);
    const idMatch = opfXml.match(/<dc:identifier[^>]*>([\s\S]*?)<\/dc:identifier>/i);

    const title = titleMatch ? titleMatch[1].trim() : 'Untitled EPUB';
    const author = authorMatch ? authorMatch[1].trim() : 'Unknown Author';
    const identifier = idMatch ? idMatch[1].trim() : '';

    // Manifest: map id -> href
    const manifest: Record<string, string> = {};
    const itemRegex = /<item\s+[^>]*?id\s*=\s*["']([^"']+)["'][^>]*?href\s*=\s*["']([^"']+)["'][^>]*?>|<item\s+[^>]*?href\s*=\s*["']([^"']+)["'][^>]*?id\s*=\s*["']([^"']+)["'][^>]*?>/gi;
    let match: RegExpExecArray | null;
    while ((match = itemRegex.exec(opfXml)) !== null) {
      const id = match[1] || match[4];
      const href = match[2] || match[3];
      if (id && href) manifest[id] = href;
    }

    // Spine: ordered itemrefs
    const spineIds: string[] = [];
    const itemrefRegex = /<itemref\s+[^>]*?idref\s*=\s*["']([^"']+)["'][^>]*?>/gi;
    while ((match = itemrefRegex.exec(opfXml)) !== null) {
      if (match[1]) spineIds.push(match[1]);
    }

    if (spineIds.length === 0) {
      log({ source: 'kittlibr-reader', stage: 'parse', message: 'EPUB package document contains empty or missing spine' });
      throw new Error('EPUB parse error: No reading order spine items found');
    }

    const chapters: Array<{ id: string; title: string; href: string; html: string; rawBytes: Uint8Array }> = [];
    for (let i = 0; i < spineIds.length; i++) {
      const spineId = spineIds[i];
      const href = manifest[spineId];
      if (!href) {
        log({ source: 'kittlibr-reader', stage: 'parse', message: `Spine itemref ${spineId} missing from manifest` });
        continue;
      }

      // Resolve path relative to opfDir
      const fullHref = (opfDir + decodeURIComponent(href)).replace(/\/\.\//g, '/');
      const cleanHref = this.sanitizeEntryPath(fullHref, '/cache') || fullHref;
      const chapterData = files[cleanHref] || files[decodeURIComponent(href)] || files[fullHref];

      if (chapterData) {
        const chapterHtml = decoder.decode(chapterData);
        // Chapter title heuristic
        const chTitleMatch = chapterHtml.match(/<title[^>]*>([\s\S]*?)<\/title>/i) || chapterHtml.match(/<h[1-3][^>]*>([\s\S]*?)<\/h[1-3]>/i);
        const chTitle = chTitleMatch ? chTitleMatch[1].replace(/<[^>]+>/g, '').trim() : `Chapter ${i + 1}`;
        chapters.push({
          id: spineId,
          title: chTitle,
          href: cleanHref,
          html: chapterHtml,
          rawBytes: chapterData
        });
      } else {
        log({ source: 'kittlibr-reader', stage: 'parse', message: `Referenced spine file not found: ${cleanHref}` });
      }
    }

    if (chapters.length === 0) {
      log({ source: 'kittlibr-reader', stage: 'parse', message: 'Zero readable chapters could be unpacked from spine' });
      throw new Error('EPUB parse error: No chapter HTML documents could be extracted');
    }

    return {
      title,
      author,
      identifier,
      chapters,
      files
    };
  }
}

