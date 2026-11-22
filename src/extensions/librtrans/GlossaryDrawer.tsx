import React, { useState } from 'react';
import { GlossaryEntry } from './librtrans_pipeline';

interface GlossaryDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  glossary: Map<string, GlossaryEntry>;
  seriesSlug: string;
  onSaveGlossary: (updated: Map<string, GlossaryEntry>) => void;
}

export const GlossaryDrawer: React.FC<GlossaryDrawerProps> = ({
  isOpen,
  onClose,
  glossary,
  seriesSlug,
  onSaveGlossary
}) => {
  if (!isOpen) return null;

  const [entries, setEntries] = useState<GlossaryEntry[]>(Array.from(glossary.values()));
  const [newSource, setNewSource] = useState('');
  const [newTarget, setNewTarget] = useState('');
  const [newPronoun, setNewPronoun] = useState<'he/him' | 'she/her' | 'they/them' | 'it/its'>('he/him');

  const handleAdd = () => {
    if (!newSource || !newTarget) return;
    const next = [...entries, { sourceTerm: newSource, targetTerm: newTarget, pronounLock: newPronoun }];
    setEntries(next);
    const m = new Map<string, GlossaryEntry>();
    next.forEach(e => m.set(e.sourceTerm, e));
    onSaveGlossary(m);
    setNewSource('');
    setNewTarget('');
  };

  return (
    <div style={{ position: 'fixed', top: 0, right: 0, bottom: 0, width: '380px', background: '#1E1E2E', borderLeft: '2px solid #2E1A47', boxShadow: '-4px 0 10px rgba(0,0,0,0.5)', zIndex: 1200, display: 'flex', flexDirection: 'column', color: '#E6E6FA', padding: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #2E1A47', paddingBottom: '10px' }}>
        <h3 style={{ margin: 0, fontSize: '15px', color: '#FFFBEB' }}>📖 {seriesSlug}.glossary.yaml</h3>
        <button onClick={onClose} style={{ background: 'transparent', border: 'none', color: '#FFFBEB', fontSize: '18px', cursor: 'pointer' }}>×</button>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', margin: '12px 0' }}>
        <p style={{ fontSize: '11px', color: '#B5B0D0', margin: '0 0 8px 0' }}>Deterministic entity masks and pronoun locks for translation continuity:</p>
        <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
          {entries.map((item, idx) => (
            <li key={idx} style={{ background: '#252538', padding: '8px', borderRadius: '8px', marginBottom: '6px', fontSize: '12px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <strong style={{ color: '#FFD700' }}>{item.sourceTerm}</strong>
                <span style={{ color: '#98FB98' }}>→ {item.targetTerm}</span>
              </div>
              {item.pronounLock && (
                <div style={{ fontSize: '10px', color: '#B5B0D0', marginTop: '4px' }}>🔒 Pronoun Lock: {item.pronounLock}</div>
              )}
            </li>
          ))}
        </ul>
      </div>

      <div style={{ borderTop: '1px solid #2E1A47', paddingTop: '10px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
        <h4 style={{ margin: 0, fontSize: '12px', color: '#FFFBEB' }}>Add Term Lock</h4>
        <input
          type="text"
          placeholder="Source Term (e.g.  주인공)"
          value={newSource}
          onChange={e => setNewSource(e.target.value)}
          style={{ background: '#2B2B3D', border: '1px solid #4E4E6A', color: '#FFFBEB', padding: '6px', borderRadius: '6px', fontSize: '12px' }}
        />
        <input
          type="text"
          placeholder="Target Term (e.g. Arthur)"
          value={newTarget}
          onChange={e => setNewTarget(e.target.value)}
          style={{ background: '#2B2B3D', border: '1px solid #4E4E6A', color: '#FFFBEB', padding: '6px', borderRadius: '6px', fontSize: '12px' }}
        />
        <select
          value={newPronoun}
          onChange={e => setNewPronoun(e.target.value as any)}
          style={{ background: '#2B2B3D', border: '1px solid #4E4E6A', color: '#FFFBEB', padding: '6px', borderRadius: '6px', fontSize: '12px' }}
        >
          <option value="he/him">Pronoun Lock: he/him</option>
          <option value="she/her">Pronoun Lock: she/her</option>
          <option value="they/them">Pronoun Lock: they/them</option>
          <option value="it/its">Pronoun Lock: it/its</option>
        </select>
        <button
          onClick={handleAdd}
          style={{ background: '#2E1A47', border: '1px solid #7B68EE', color: '#FFFBEB', padding: '8px', borderRadius: '8px', cursor: 'pointer', fontWeight: 'bold' }}
        >
          Add to Glossary
        </button>
      </div>
    </div>
  );
};
