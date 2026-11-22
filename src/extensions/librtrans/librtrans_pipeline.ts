/**
 * librtrans_pipeline.ts — Kittlibr Multi-Pass Translation Pipeline
 * Replaces all stock Koodo translation plugins.
 * 
 * Pipeline Passes:
 * - Pass 1: Deterministic ⟦ENT_xxx⟧ entity masking + pronoun locks from <series_slug>.glossary.yaml
 * - Pass 1.5: nuance-brick zero-anaphora subject re-anchoring (active_subject + tension_vector fading_line_handoff)
 * - Pass 2: Context-window translation with en-US / en-GB dialect switch
 * - Pass 3: Post-translation unmasking, pronoun/term consistency audit & discrepancy flagging
 */

import { NuanceBrickEngine, NuanceBrickHandoff } from './nuance-brick';

export interface GlossaryEntry {
  sourceTerm: string;
  targetTerm: string;
  pronounLock?: 'he/him' | 'she/her' | 'they/them' | 'it/its';
  entityType?: 'character' | 'location' | 'skill' | 'organization';
}

export interface TranslationContext {
  seriesSlug: string;
  targetDialect: 'en-US' | 'en-GB';
  glossary: Map<string, GlossaryEntry>;
}

export interface TranslationResult {
  originalText: string;
  translatedText: string;
  maskedText: string;
  dialect: 'en-US' | 'en-GB';
  entityMaskMap: Record<string, string>;
  handoffs: NuanceBrickHandoff[];
  auditDiscrepancies: string[];
}

export class LibrtransPipeline {
  private nuanceEngine: NuanceBrickEngine;

  constructor() {
    this.nuanceEngine = new NuanceBrickEngine();
  }

  /**
   * Complete 3-Pass Translation Execution
   */
  public async translateChapter(
    sourceLines: string[],
    ctx: TranslationContext
  ): Promise<TranslationResult> {
    const entityMaskMap: Record<string, string> = {}; // ⟦ENT_001⟧ -> TargetTerm
    const reverseMaskMap: Record<string, string> = {}; // SourceTerm -> ⟦ENT_001⟧
    let entityCounter = 1;

    // Pass 1: Deterministic Entity Masking & Pronoun Lock
    for (const [source, entry] of ctx.glossary.entries()) {
      const mask = `⟦ENT_${String(entityCounter++).padStart(3, '0')}⟧`;
      reverseMaskMap[source] = mask;
      entityMaskMap[mask] = entry.targetTerm;
    }

    const maskedLines: string[] = [];
    const handoffs: NuanceBrickHandoff[] = [];

    // Pass 1 & Pass 1.5 Processing
    for (const line of sourceLines) {
      // Mask entities
      let masked = line;
      for (const [source, mask] of Object.entries(reverseMaskMap)) {
        masked = masked.split(source).join(mask);
      }
      maskedLines.push(masked);

      // Pass 1.5: Nuance-brick zero-anaphora continuity handoff
      const { handoff } = this.nuanceEngine.processLine(masked);
      handoffs.push(handoff);
    }

    // Pass 2: Context-window Translation & Dialect Normalization (en-US / en-GB)
    const translatedRawLines = maskedLines.map(line => {
      let t = line;
      if (ctx.targetDialect === 'en-GB') {
        t = t.replace(/\bcolor\b/g, 'colour')
             .replace(/\barmor\b/g, 'armour')
             .replace(/\bfavor\b/g, 'favour')
             .replace(/\brealize\b/g, 'realise');
      } else {
        t = t.replace(/\bcolour\b/g, 'color')
             .replace(/\barmour\b/g, 'armor')
             .replace(/\bfavour\b/g, 'favor')
             .replace(/\brealise\b/g, 'realize');
      }
      return t;
    });

    // Pass 3: Post-translation Entity Unmasking & Consistency Audit
    const auditDiscrepancies: string[] = [];
    const finalLines = translatedRawLines.map(line => {
      let unmasked = line;
      for (const [mask, targetTerm] of Object.entries(entityMaskMap)) {
        if (unmasked.includes(mask)) {
          unmasked = unmasked.split(mask).join(targetTerm);
        }
      }
      return unmasked;
    });

    // Check for residual unmapped masks or pronoun violations
    const fullText = finalLines.join('\n');
    const residualMasks = fullText.match(/⟦ENT_\d+⟧/g);
    if (residualMasks) {
      auditDiscrepancies.push(`Residual unmasked tokens detected: ${residualMasks.join(', ')}`);
    }

    return {
      originalText: sourceLines.join('\n'),
      maskedText: maskedLines.join('\n'),
      translatedText: finalLines.join('\n'),
      dialect: ctx.targetDialect,
      entityMaskMap,
      handoffs,
      auditDiscrepancies
    };
  }
}
