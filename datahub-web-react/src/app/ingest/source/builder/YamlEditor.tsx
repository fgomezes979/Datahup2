import React from 'react';
import Editor from '@monaco-editor/react';

type Props = {
    initialText: string;
    height?: string;
    onChange: (change: any) => void;
};

export const YamlEditor = ({ initialText, height, onChange }: Props) => {
    return (
        <Editor
            options={{
                minimap: { enabled: false },
                scrollbar: {
                    vertical: 'hidden',
                    horizontal: 'hidden',
                },
            }}
            height={height || '55vh'}
            defaultLanguage="yaml"
            value={initialText}
            onChange={onChange}
        />
    );
};
