import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface MarkdownViewProps {
  content: string;
}

export const MarkdownView: React.FC<MarkdownViewProps> = ({ content }) => {
  return (
    <div className="markdown-body">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children }) => <h3 className="md-heading md-h1">{children}</h3>,
          h2: ({ children }) => <h4 className="md-heading md-h2">{children}</h4>,
          h3: ({ children }) => <h5 className="md-heading md-h3">{children}</h5>,
          h4: ({ children }) => <h6 className="md-heading md-h4">{children}</h6>,
          h5: ({ children }) => <h6 className="md-heading md-h5">{children}</h6>,
          h6: ({ children }) => <h6 className="md-heading md-h6">{children}</h6>,
          p: ({ children }) => <p className="md-p">{children}</p>,
          ul: ({ children }) => <ul className="md-ul">{children}</ul>,
          ol: ({ children }) => <ol className="md-ol">{children}</ol>,
          li: ({ children }) => <li className="md-li">{children}</li>,
          strong: ({ children }) => <strong className="md-strong">{children}</strong>,
          em: ({ children }) => <em className="md-em">{children}</em>,
          del: ({ children }) => <del className="md-del">{children}</del>,
          blockquote: ({ children }) => <blockquote className="md-blockquote">{children}</blockquote>,
          hr: () => <hr className="md-hr" />,
          a: ({ children, href, ...props }) => (
            <a className="md-link" href={href} target="_blank" rel="noopener noreferrer" {...props}>
              {children}
            </a>
          ),
          table: ({ children }) => (
            <div className="md-table-wrapper">
              <table className="md-table">{children}</table>
            </div>
          ),
          thead: ({ children }) => <thead className="md-thead">{children}</thead>,
          tbody: ({ children }) => <tbody className="md-tbody">{children}</tbody>,
          tr: ({ children }) => <tr className="md-tr">{children}</tr>,
          th: ({ children }) => <th className="md-th">{children}</th>,
          td: ({ children }) => <td className="md-td">{children}</td>,
          pre: ({ children }) => <pre className="md-pre">{children}</pre>,
          code({ className, children, ...props }) {
            const isInline = !className && typeof children === 'string' && !children.includes('\n');
            if (isInline) {
              return (
                <code className="md-inline-code" {...props}>
                  {children}
                </code>
              );
            }
            return (
              <code className={`md-code-block ${className || ''}`.trim()} {...props}>
                {children}
              </code>
            );
          },
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
};
