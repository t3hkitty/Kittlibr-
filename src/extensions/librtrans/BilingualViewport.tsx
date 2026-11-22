import React, { useState } from 'react';
import { TranslationResult } from '../librtrans_pipeline';

interface BilingualViewportProps {
  originalLines: string[];
  translatedLines: string[];
  dialect: 'en-US' | 'en-GB';
  onDialectChange: (dialect: 'en-US' | 'en-GB') => void;
  onOpenGlossary: () => void;
}

export const BilingualViewport: React.FC<BilingualViewportProps> = ({
  originalLines,
  translatedLines,
  dialect,
  onDialectChange,
  onOpenGlossary
}) => {
  const [syncedScroll, setSyncedScroll] = useState(true);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#1E1E2E', color: '#E6E6FA' }}>
      {/* Top Controls Bar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 16px', background: '#252538', borderBottom: '2px solid #2E1A47' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontWeight: 'bold', color: '#FFFBEB', fontSize: '13px' }}>🌐 librtrans Bilingual View</span>
          <span style={{ fontSize: '11px', background: '#2E1A47', padding: '2px 8px', borderRadius: '10px' }}>Pass 1-3 Unified</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <label style={{ fontSize: '12px' }}>Dialect:</label>
          <select
            value={dialect}
            onChange={e => onDialectChange(e.target.value as 'en-US' | 'en-GB')}
            style={{ background: '#2B2B3D', color: '#FFFBEB', border: '1px solid #7B68EE', borderRadius: '6px', padding: '3px 8px' }}
          >
            <option value="en-US">en-US (American)</option>
            <option value="en-GB">en-GB (British)</option>
          </select>
          <button
            onClick={onOpenGlossary}
            style={{ background: '#4B0082', color: '#FFFBEB', border: '1px solid #9370DB', borderRadius: '6px', padding: '4px 10px', fontSize: '11px', cursor: 'pointer' }}
          >
            📖 Glossary Drawer
          </button>
        </div>
      </div>

      {/* Split Viewport */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', flex: 1, overflow: 'hidden' }}>
        {/* Source Text Column */}
        <div style={{ padding: '16px', overflowY: 'auto', borderRight: '1px solid #2E1A47', background: '#181825', fontFamily: 'serif', lineHeight: 1.6 }}>
          <h4 style={{ margin: '0 0 12px 0', color: '#B5B0D0', fontSize: '12px', textTransform: 'uppercase' }}>Source (Raw)</h4>
          {originalLines.map((line, idx) => (
            <p key={`src-${idx}`} style={{ margin: '0 0 12px 0' }}>{line}</p>
          ))}
        </div>

        {/* Translated Text Column */}
        <div style={{ padding: '16px', overflowY: 'auto', background: '#1E1E2E', fontFamily: 'serif', lineHeight: 1.6 }}>
          <h4 style={{ margin: '0 0 12px 0', color: '#98FB98', fontSize: '12px', textTransform: 'uppercase' }}>Translated ({dialect})</h4>
          {translatedLines.map((line, idx) => (
            <p key={`trans-${idx}`} style={{ margin: '0 0 12px 0' }}>{line}</p>
          ))}
        </div>
      </div>
    </div>
  );
};
