import { Component } from '@angular/core';
import { DocumentListComponent } from './components/document-list/document-list.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [DocumentListComponent],
  template: `
    <div style="padding: 20px; background-color: #007bff; color: white; border-bottom: 1px solid #0056b3;">
      <h1 style="margin: 0;">Email Processor AI</h1>
    </div>
    <main style="padding: 20px;">
      <app-document-list></app-document-list>
    </main>
  `,
  styles: [`
    :host {
      display: block;
      font-family: Arial, sans-serif;
    }
  `]
})
export class AppComponent {
  title = 'Email Processor';
}
