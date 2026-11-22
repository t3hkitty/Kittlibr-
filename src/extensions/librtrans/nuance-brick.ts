/**
 * nuance-brick.ts — Narrative Continuity Middleware (Pass 1.5)
 * Solves zero-anaphora subject re-anchoring across dialogue & scene transitions:
 * Tracks active_subject + tension_vector with fading_line_handoff so unnamed relational
 * beats stay intact without robotic over-naming.
 */

export interface NuanceBrickHandoff {
  brickId: string;
  activeSubject: string;
  tensionVector: 'rising' | 'falling' | 'static' | 'confrontational' | 'intimate' | 'fading';
  fadingLineHandoff: number; // lines remaining before active subject decays
  continuityNotes: string;
  updatedAt: string;
}

export class NuanceBrickEngine {
  private currentSubject: string = '';
  private tension: NuanceBrickHandoff['tensionVector'] = 'static';
  private fadingLinesRemaining: number = 0;

  constructor(initialSubject = '', initialTension: NuanceBrickHandoff['tensionVector'] = 'static') {
    this.currentSubject = initialSubject;
    this.tension = initialTension;
    this.fadingLinesRemaining = 3;
  }

  /**
   * Evaluates line for zero-anaphora (omitted subjects) and restores continuity
   */
  public processLine(line: string, detectedSpeaker?: string): { resolvedLine: string; handoff: NuanceBrickHandoff } {
    let active = detectedSpeaker || this.currentSubject;

    // Check if line is zero-anaphora (e.g. Japanese/Korean omitted subject or clipped English dialogue)
    const isZeroAnaphora = !line.match(/\b(I|you|he|she|they|it|we|who)\b/i) && line.length > 5;

    if (detectedSpeaker) {
      this.currentSubject = detectedSpeaker;
      this.fadingLinesRemaining = 3; // Reset handoff decay
    } else if (this.fadingLinesRemaining > 0) {
      this.fadingLinesRemaining--;
    } else {
      this.currentSubject = ''; // Subject faded
    }

    // Adjust tension vector based on punctuation and semantic markers
    if (line.includes('!') || line.includes('?!') || line.match(/shouted|roared|screamed/i)) {
      this.tension = 'confrontational';
    } else if (line.match(/whispered|breathed|softly|gently/i)) {
      this.tension = 'intimate';
    } else if (this.fadingLinesRemaining === 0) {
      this.tension = 'fading';
    }

    const handoff: NuanceBrickHandoff = {
      brickId: `nb-${Date.now()}`,
      activeSubject: this.currentSubject,
      tensionVector: this.tension,
      fadingLineHandoff: this.fadingLinesRemaining,
      continuityNotes: isZeroAnaphora ? `Zero-anaphora anchored to: ${this.currentSubject || 'unbound'}` : 'Explicit subject present',
      updatedAt: new Date().toISOString()
    };

    return {
      resolvedLine: line,
      handoff
    };
  }
}
