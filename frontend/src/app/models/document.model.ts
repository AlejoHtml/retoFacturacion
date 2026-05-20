export interface ProcessedDocument {
  id: string;
  invoiceNumber: string;
  date: string;
  sender: string;
  filePath: string;
  extractedData: { [key: string]: string };
  processedAt: string;
}