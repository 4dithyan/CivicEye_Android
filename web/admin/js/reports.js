async function generateReport() {
    const startDateVal = document.getElementById('startDate').value;
    const endDateVal = document.getElementById('endDate').value;

    if (!startDateVal || !endDateVal) {
        adminLogic.toast('Please select both start and end dates.', 'error');
        return;
    }

    const start = new Date(startDateVal);
    const end = new Date(endDateVal);
    end.setHours(23, 59, 59, 999); // Include the entire end day

    adminLogic.toast('Generating Report...', 'info');

    try {
        // Fetch Data
        const [issuesSnap, staffSnap] = await Promise.all([
            db.collection('issues')
                .where('createdAt', '>=', start)
                .where('createdAt', '<=', end)
                .get(),
            db.collection('users').where('role', '==', 'staff').get()
        ]);

        const issues = issuesSnap.docs.map(doc => ({ ...doc.data(), id: doc.id }));

        // Create Staff Lookup Map
        const staffMap = {};
        staffSnap.docs.forEach(doc => {
            const data = doc.data();
            staffMap[doc.id] = data.name || 'Unknown Staff';
        });

        if (issues.length === 0) {
            adminLogic.toast('No issues found for this period.', 'info');
            return;
        }

        // Preview Stats
        const total = issues.length;
        const resolved = issues.filter(i => i.status === 'resolved' || i.status === 'verified').length;
        const pending = issues.filter(i => i.status === 'pending').length;

        document.getElementById('statsPreview').style.display = 'block';
        document.getElementById('previewTotal').textContent = total;
        document.getElementById('previewResolved').textContent = resolved;
        document.getElementById('previewPending').textContent = pending;

        // PDF Generation
        const { jsPDF } = window.jspdf;
        const doc = new jsPDF();

        // Header
        doc.setFontSize(20);
        doc.setTextColor(30, 58, 138); // Brand Blue
        doc.text("CivicEye Issue Report", 14, 20);

        doc.setFontSize(10);
        doc.setTextColor(100);
        doc.text(`Generated on: ${new Date().toLocaleString()}`, 14, 28);
        doc.text(`Period: ${startDateVal} to ${endDateVal}`, 14, 34);

        // Stats Summary
        doc.setFillColor(241, 245, 249);
        doc.rect(14, 40, 182, 24, 'F');
        doc.setFontSize(12);
        doc.setTextColor(0);
        doc.text(`Total Issues: ${total}`, 20, 56);
        doc.text(`Resolved: ${resolved}`, 80, 56);
        doc.text(`Pending: ${pending}`, 140, 56);

        // Table
        const tableData = issues.map(i => {
            const staffName = i.assignedTo ? (staffMap[i.assignedTo] || 'Unknown ID') : 'Unassigned';
            return [
                i.title || 'N/A',
                i.category || 'General',
                i.status.toUpperCase(),
                staffName,
                new Date(i.createdAt.seconds * 1000).toLocaleDateString()
            ];
        });

        doc.autoTable({
            startY: 70,
            head: [['Title', 'Category', 'Status', 'Assigned Staff', 'Date']],
            body: tableData,
            theme: 'grid',
            headStyles: { fillColor: [30, 58, 138], textColor: 255 },
            styles: { fontSize: 9, cellPadding: 3 },
            alternateRowStyles: { fillColor: [248, 250, 252] }
        });

        // Save
        doc.save(`CivicEye_Report_${startDateVal}_${endDateVal}.pdf`);
        adminLogic.toast('PDF Downloaded successfully!', 'success');

    } catch (error) {
        console.error("Report Error:", error);
        adminLogic.toast('Failed to generate report: ' + error.message, 'error');
    }
}
