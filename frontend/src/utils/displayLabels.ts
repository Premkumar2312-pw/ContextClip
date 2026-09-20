/**
 * Shared human-friendly label formatting for clipboard types, technologies, and categories.
 * Prevents raw uppercase enums (e.g. TERMINAL_COMMAND, PLAIN_TEXT, UNKNOWN) from leaking into UI.
 */

const KNOWN_LABELS: Record<string, string> = {
  // Types & Syntax Formats
  TERMINAL_COMMAND: 'Terminal Command',
  COMMAND: 'Terminal Command',
  PLAIN_TEXT: 'Plain Text',
  TEXT: 'Plain Text',
  CODE: 'Code',
  JSON: 'JSON',
  SQL: 'SQL',
  URL: 'URL',
  EMAIL: 'Email',
  UUID: 'UUID',
  IP_ADDRESS: 'IP Address',
  FILE_PATH: 'File Path',
  PHONE_NUMBER: 'Phone Number',
  PHONE: 'Phone Number',
  IMAGE: 'Image',
  ERROR_LOG: 'Error Log',
  CONFIG: 'Configuration',
  CONFIGURATION: 'Configuration',
  DOCUMENTATION: 'Documentation',
  MARKDOWN: 'Markdown',
  YAML: 'YAML',
  XML: 'XML',
  CSV: 'CSV',
  LOG: 'Log',
  ERROR: 'Error',

  // Categories
  GENERAL: 'General',
  PROGRAMMING: 'Programming',
  DEVOPS: 'DevOps',
  DATABASE: 'Database',
  WEB: 'Web',
  API: 'API',
  CLOUD: 'Cloud',
  SYSTEM: 'System',
  TROUBLESHOOTING: 'Troubleshooting',
  COMMUNICATION: 'Communication',

  // Technologies & Languages
  UNKNOWN: 'Unknown',
  JAVA: 'Java',
  PYTHON: 'Python',
  TYPESCRIPT: 'TypeScript',
  JAVASCRIPT: 'JavaScript',
  KOTLIN: 'Kotlin',
  GO: 'Go',
  RUST: 'Rust',
  CSHARP: 'C#',
  PHP: 'PHP',
  HTML: 'HTML',
  CSS: 'CSS',
  SHELL: 'Shell',
  BASH: 'Bash',
  POWERSHELL: 'PowerShell',
  SPRING_BOOT: 'Spring Boot',
  SPRING: 'Spring',
  REACT: 'React',
  NODE_JS: 'Node.js',
  NODEJS: 'Node.js',
  NODE: 'Node.js',
  DOCKER: 'Docker',
  KUBERNETES: 'Kubernetes',
  POSTGRESQL: 'PostgreSQL',
  POSTGRES: 'PostgreSQL',
  MYSQL: 'MySQL',
  MONGODB: 'MongoDB',
  REDIS: 'Redis',
  SQLITE: 'SQLite',
  DOTNET: '.NET',
  DJANGO: 'Django',
  FLASK: 'Flask',
  FASTAPI: 'FastAPI',
  PANDAS: 'Pandas',
  NUMPY: 'NumPy',
  GIT: 'Git',
  TERRAFORM: 'Terraform',
  ANSIBLE: 'Ansible',
  AWS: 'AWS',
  LINUX: 'Linux',
};

/**
 * Converts any raw backend enum or identifier into a clean, human-friendly title.
 * E.g.: 'TERMINAL_COMMAND' -> 'Terminal Command', 'UNKNOWN' -> 'Unknown'
 */
export function formatLabel(value: string | null | undefined): string {
  if (!value) return '';
  const trimmed = value.trim();
  if (!trimmed) return '';

  const upper = trimmed.toUpperCase();
  if (KNOWN_LABELS[upper]) {
    return KNOWN_LABELS[upper];
  }

  // Fallback: title-case with underscore replacement
  return trimmed
    .split(/[\s_]+/)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ');
}
