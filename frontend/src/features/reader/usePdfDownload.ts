import { useMutation } from '@tanstack/react-query';
import { apiUrl } from '../../services/api/client';
import { readerApi } from '../../services/api/endpoints';

/**
 * Downloads a book's PDF: asks for a short-lived download ticket, then navigates a hidden link to
 * the content URL. The server answers with Content-Disposition: attachment, so the page stays put.
 */
export function usePdfDownload(bookId: string) {
  return useMutation({
    mutationFn: () => readerApi.downloadAccess(bookId),
    onSuccess: (grant) => {
      const a = document.createElement('a');
      a.href = apiUrl(grant.contentUrl);
      a.rel = 'noopener';
      document.body.appendChild(a);
      a.click();
      a.remove();
    },
  });
}
