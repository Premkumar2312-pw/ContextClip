import React from 'react';
import ReactMarkdown from 'react-markdown';

interface MarkdownViewProps {
  content: string;
}

export const MarkdownView: React.FC<MarkdownViewProps> = ({ content }) => {
  return (
    <div className="markdown-body">
      <ReactMarkdown
        components={{
          h1: ({ children }) => <h3 className="md-heading md-h1">{children}</h3>,
          h2: ({ children }) => <h4 className="md-heading md-h2">{children}</h4>,
          h3: ({ children }) => <h5 className="md-heading md-h3">{children}</h5>,
          p: ({ children }) => <p className="md-p">{children}</p>,
          ul: ({ children }) => <ul className="md-ul">{children}</ul>,
          ol: ({ children }) => <ol className="md-ol">{children}</ol>,
          li: ({ children }) => <li className="md-li">{children}</li>,
          strong: ({ children }) => <strong className="md-strong">{children}</strong>,
          em: ({ children }) => <em className="md-em">{children}</em>,
          code({ className, children, ...props }) {
            const match = /language-(\w+)/.exec(className || '');
            const isInline = !match && typeof children === 'string' && !children.includes('\n');
            if (isInline) {
              return (
                <code className="md-inline-code" {...props}>
                  {children}
                </code>
              );
            }
            return (
              <pre className="md-pre">
                <code className="md-code-block" {...props}>
                  {children}
                </code>
              </pre>
            );
          },
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
};
